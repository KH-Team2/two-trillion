import json
from pathlib import Path

# tax_guides.json 파일 위치
DATA_PATH = Path(__file__).resolve().parents[1] / "data" / "tax_guides.json"


def load_guides() -> list[dict]:
    """맞춤 절세 가이드 JSON 데이터를 불러온다."""
    with DATA_PATH.open(encoding="utf-8") as file:
        return json.load(file)


def find_guide(code: str) -> dict | None:
    """추천 코드로 해당 절세 가이드 정보를 찾는다."""
    return next(
        (guide for guide in load_guides() if guide["code"] == code),
        None,
    )
