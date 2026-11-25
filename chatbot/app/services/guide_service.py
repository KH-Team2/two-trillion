
import json
import os
import re
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.repositories.guide_repository import find_guide


class GuideGenerationError(RuntimeError):
    """절세 가이드 설명 생성 실패."""


def generate_guide(code: str) -> dict:
    """추천 코드에 해당하는 상품 설명을 생성한다."""

    # 1. 검증된 JSON에서 상품 정보 조회
    guide = find_guide(code)

    if guide is None:
        raise ValueError("지원하지 않는 추천 코드입니다.")

    facts = guide["facts"]

    # 2. Gemma에게 전달할 프롬프트
    prompt = (
        "당신은 연말정산을 처음 접하는 사용자를 위한 안내문 작성 도우미입니다.\n"
        "제공된 JSON의 facts만 사용해 금융상품을 설명하세요.\n\n"
        
        "[작성 조건]\n"
        "1. 반드시 JSON 객체만 출력하세요.\n"
        "2. 키는 description, taxBenefit, nextStep 세 개만 사용하세요.\n"
        "3. description: 상품의 의미와 장점을 설명하세요.\n"
        "4. taxBenefit: 연말정산 혜택과 세금에 미치는 영향을 설명하세요.\n"
        "5. nextStep: 사용자가 확인할 사항을 설명하세요.\n"
        "6. 자연스러운 한국어 문장으로 작성하세요.\n"
        "7. 가입을 권유하거나 절세 효과를 보장하지 마세요.\n"
        "8. 소득공제와 세액공제를 정확히 구분하세요.\n"
        "9. 제공된 facts에 없는 사실이나 수치를 추가하지 마세요.\n"
        "10. 숫자를 사용하지 마세요.\n"
        "11. 제목, 목록, Markdown 문법은 사용하지 마세요.\n\n"

        "[상품 정보]\n"
        + json.dumps(
            {"name": guide["name"], "facts": facts},
            ensure_ascii=False,
        )
    )

    # 3. 기존 Ollama 설정 재사용
    base_url = os.getenv(
        "OLLAMA_BASE_URL", "http://localhost:11434"
    ).rstrip("/")
    model = os.getenv("OLLAMA_MODEL", "gemma3:4b")

    body = json.dumps({
        "model": model,
        "stream": False,
        "format": "json",
        "options": {"temperature": 0},
        "messages": [
            {"role": "user", "content": prompt}
        ],
    }).encode("utf-8")

    request = Request(
        f"{base_url}/api/chat",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    # 4. Gemma 응답 처리
    try:
        with urlopen(request, timeout=60) as response:
            result = json.load(response)

        
        content = json.loads(result["message"]["content"])

        required = ("description", "taxBenefit", "nextStep")

        if (
            not isinstance(content, dict)
            or set(content) != set(required)
            or any(
                not isinstance(content[key], str)
                or not content[key].strip()
                for key in required
            )
        ):
            raise GuideGenerationError("설명 형식이 올바르지 않습니다.")

        content = {
            key: content[key].strip()
            for key in required
        }

        text = " ".join(content.values())

        # 임의의 숫자 생성이나 공제 유형 혼동 방지
        expected = (
            "소득공제" if code == "HOUSING_SAVINGS"
            else "세액공제"
        )
        opposite = (
            "세액공제" if expected == "소득공제"
            else "소득공제"
        )

        if (
            len(text) > 1500
            or re.search(r"\d", text)
            or expected not in content["taxBenefit"]
            or opposite in text
        ):
            raise GuideGenerationError("생성된 설명의 검증에 실패했습니다.")

        source = "gemma"

    except (
        HTTPError,
        URLError,
        TimeoutError,
        OSError,
        ValueError,
        KeyError,
        TypeError,
        GuideGenerationError,
    ):
        # 5. 장애 또는 검증 실패 시 기본 설명 사용
        parts = re.split(r"(?<=\.)\s+", guide["fallback_content"])

        if len(parts) != 3:
            raise GuideGenerationError(
                "기본 설명은 세 문장으로 구성되어야 합니다."
            )

        content = dict(zip(
            ("description", "taxBenefit", "nextStep"),
            parts,
        ))
        source = "fallback"


    return {
        "code": code,
        "name": guide["name"],
        "content": content,
        "source": source,
    }
