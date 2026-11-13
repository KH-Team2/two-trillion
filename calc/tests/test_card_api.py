"""
    POST /card/parse API 테스트
    (실행: calc 폴더에서 `pytest tests/test_card_api.py`)

    - TestClient: 서버를 실제로 켜지 않고 API를 불러 보는 도구
    - 진짜 DB 목록(8만 행) 대신 가짜 분류기(목록 2줄)를 끼워 넣어요 (dependency_overrides)
    - 파일은 간단한 csv를 글자로 직접 만들어 보내요
"""

import pytest
from fastapi.testclient import TestClient

from app.api.card import get_matcher
from app.card.matcher import MerchantMatcher, MerchantRow
from app.main import app

TOKEN = "test-token"

# 가짜 분류기: 「서울교통공사」 = 대중교통(10번), 「시장」이 들어가면 전통시장(20번)
FAKE_MATCHER = MerchantMatcher(
    [
        MerchantRow(
            list_id=10,
            normalized_name="서울교통공사",
            match_type="EXACT",
            category_code="TRANSIT",
        ),
        MerchantRow(
            list_id=20, normalized_name="시장", match_type="CONTAINS", category_code="MARKET"
        ),
    ]
)

# 사용구분 칸이 있는 csv (결제수단을 파일에서 읽음)
CSV_WITH_PAY = (
    "승인일시,가맹점명,이용금액,사용구분\n"
    "2026-03-02 08:12,㈜서울교통공사,\"1,550\",신용\n"
    "2026-03-03 12:00,통인시장 떡볶이,8000,체크\n"
    "2026-03-04 19:30,스타벅스 광화문점,5600,신용\n"
)

# 사용구분 칸이 없는 csv (사용자가 신용/체크를 골라야 함)
CSV_NO_PAY = "승인일시,가맹점명,이용금액\n2026-03-02,서울교통공사,1550\n"


@pytest.fixture
def client(monkeypatch):
    """토큰 설정 + 가짜 분류기를 끼운 TestClient (테스트가 끝나면 원래대로)"""
    monkeypatch.setenv("CALC_API_TOKEN", TOKEN)
    app.dependency_overrides[get_matcher] = lambda: FAKE_MATCHER
    yield TestClient(app, headers={"X-Calc-Token": TOKEN})
    app.dependency_overrides.clear()


def upload(client, text, filename="card.csv", pay_method=None):
    """csv 글자를 파일처럼 보내기 (files= 가 multipart 업로드)"""
    data = {"payMethod": pay_method} if pay_method else None
    return client.post(
        "/card/parse",
        files={"file": (filename, text.encode("utf-8"), "text/csv")},
        data=data,
    )


# ── 성공 ──
def test_parse_and_classify(client):
    """파일 해석 + 분류가 같이 되고, JSON 칸 이름이 백엔드 DTO와 같은지"""
    res = upload(client, CSV_WITH_PAY)

    assert res.status_code == 200
    body = res.json()
    assert body["rowCount"] == 3
    assert body["rows"][0] == {
        "rowNo": 2,
        "usedDate": "2026-03-02",
        "merchantName": "㈜서울교통공사",  # 원본 그대로 (정제 전)
        "payMethod": "CREDIT",
        "amount": 1550,
        "categoryCode": "TRANSIT",
        "listId": 10,
    }
    # 포함(CONTAINS) 일치 · 체크카드
    assert body["rows"][1]["categoryCode"] == "MARKET"
    assert body["rows"][1]["listId"] == 20
    assert body["rows"][1]["payMethod"] == "CHECK"
    # 목록에 없으면 일반 · listId는 null
    assert body["rows"][2]["categoryCode"] == "GENERAL"
    assert body["rows"][2]["listId"] is None


def test_user_pay_method_used_when_no_column(client):
    """사용구분 칸이 없으면 사용자가 고른 결제수단(payMethod)을 써요"""
    res = upload(client, CSV_NO_PAY, pay_method="CHECK")

    assert res.status_code == 200
    assert res.json()["rows"][0]["payMethod"] == "CHECK"


# ── 파일 오류 → 400 {"error", "message", "errorRows"} ──
def test_pay_method_required(client):
    res = upload(client, CSV_NO_PAY)

    assert res.status_code == 400
    assert res.json()["error"] == "PAY_METHOD_REQUIRED"
    assert res.json()["errorRows"] == []


def test_unsupported_extension(client):
    res = upload(client, CSV_WITH_PAY, filename="card.txt")

    assert res.status_code == 400
    assert res.json()["error"] == "UNSUPPORTED_FORMAT"


def test_validation_has_error_rows(client):
    """날짜가 이상한 줄이 있으면 그 줄 번호를 errorRows로 알려줘요"""
    bad = "승인일시,가맹점명,이용금액,사용구분\n2026-02-30,가게,1000,신용\n"
    res = upload(client, bad)

    assert res.status_code == 400
    body = res.json()
    assert body["error"] == "VALIDATION"
    assert body["errorRows"] == [2]
    assert body["message"]  # 화면 문구가 비어 있지 않은지


def test_missing_file_is_validation(client):
    """file 칸 없이 보내면 공통 입력값 오류 400 VALIDATION"""
    res = client.post("/card/parse", data={"payMethod": "CREDIT"})

    assert res.status_code == 400
    assert res.json()["error"] == "VALIDATION"


# ── 토큰 ──
def test_no_token_is_401(client):
    no_header = TestClient(app)
    res = no_header.post(
        "/card/parse",
        files={"file": ("card.csv", CSV_WITH_PAY.encode("utf-8"), "text/csv")},
    )

    assert res.status_code == 401
    assert res.json()["error"] == "UNAUTHORIZED"