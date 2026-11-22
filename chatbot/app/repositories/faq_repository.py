import json
from pathlib import Path

DATA_PATH = Path(__file__).resolve().parents[1] / "data" / "tax_terms.json"

def load_terms() -> list[dict]:
    """FAQ 원본 데이터를 읽는다."""
    with DATA_PATH.open(encoding="utf-8") as file:
        return json.load(file)

def get_categories() -> list[str]:
    """FAQ 카테고리 목록을 중복 없이 반환한다."""
    return list(dict.fromkeys(item["category"] for item in load_terms()))

def list_faqs(category: str | None = None) -> list[dict]:
    """FAQ 목록을 조회한다."""
    terms = load_terms()

    if category is not None:
        terms = [item for item in terms if item["category"] == category]

    return [
        {
            key: item[key]
            for key in ("id", "category", "term", "aliases")
        }
        for item in terms
    ]

def find_faq(faq_id: str) -> dict | None:
    """FAQ ID로 상세 데이터를 조회한다."""
    return next(
        (item for item in load_terms() if item["id"] == faq_id),
        None,
    )