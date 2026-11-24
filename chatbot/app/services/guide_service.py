
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
        "1. 상품의 의미와 장점, 연말정산 혜택, 확인할 사항을 모두 포함하세요.\n"
        "2. 자연스러운 한국어 설명문을 작성하세요.\n"
        "3. 인사말, 제목, 목록, Markdown 문법은 사용하지 마세요.\n"
        "4. 가입을 권유하거나 절세 효과를 보장하지 마세요.\n"
        "5. 소득공제와 세액공제를 정확히 구분하세요.\n"
        "6. JSON에 없는 세율, 금액, 한도, 자격 요건을 추가하지 마세요.\n"
        "7. JSON에 없는 사실을 추측하거나 만들어 내지 마세요.\n"
        "8. 본문만 출력하세요.\n\n"
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

        content = result["message"]["content"].strip()

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
            not content
            or len(content) > 1500
            or re.search(r"\d", content)
            or expected not in content
            or opposite in content
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
        content = guide["fallback_content"]
        source = "fallback"

    return {
        "code": code,
        "name": guide["name"],
        "content": content,
        "source": source,
    }
