import pytest
from fastapi.testclient import TestClient

from app.api.deps import get_criteria
from app.main import app
from tests.criteria_data import criteria

TOKEN = "test-token"


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("CALC_API_TOKEN", TOKEN)
    app.dependency_overrides[get_criteria] = criteria
    yield TestClient(app, headers={"X-Calc-Token": TOKEN})
    app.dependency_overrides.clear()


BODY = {
    "taxYear": 2026,
    "totalSalary": 30_000_000,
    "withheldTax": 600_000,
    "nationalPension": 2_700_000,
    "cardSpends": [{"categoryId": 1, "payMethod": "CREDIT", "amount": 12_000_000}],
}


def test_calculate_response_field_names_match_agreed_contract(client):
    """계산 응답 이름은 동석님 전달 그대로"""
    body = client.post("/calculate", json=BODY).json()
    assert set(body) == {
        "taxYear",
        "totalSalary",
        "withheldTax",
        "taxableIncome",
        "calculatedTax",
        "decidedTax",
        "expectedRefund",
        "details",
    }
    assert set(body["details"][0]) == {
        "typeCode",
        "typeName",
        "kind",
        "baseAmount",
        "deductionAmount",
        "sourceNote",
    }


def test_rejects_negative_input(client):
    """음수 입력은 막는다"""
    response = client.post("/calculate", json={**BODY, "totalSalary": -1})
    assert response.status_code == 400
    assert response.json()["error"] == "VALIDATION"


def test_rejects_unknown_fields(client):
    """모르는 칸은 막는다"""
    response = client.post("/calculate", json={**BODY, "memberId": 2})
    assert response.status_code == 400


def test_returns_error_for_year_without_criteria(client):
    """기준값 없는 연도"""
    response = client.post("/calculate", json={**BODY, "taxYear": 2024})
    assert response.json() == {
        "error": "NO_CRITERIA",
        "message": "해당 연도 기준값이 없어요",
    }


def test_rejects_wrong_or_missing_token(client):
    """토큰이 틀리거나 없으면 막는다"""
    assert (
        client.post(
            "/calculate", json=BODY, headers={"X-Calc-Token": "wrong"}
        ).status_code
        == 401
    )
    no_header = TestClient(app)
    assert no_header.post("/calculate", json=BODY).status_code == 401


def test_returns_401_not_500_for_non_ascii_token(client):
    """토큰에 영문이 아닌 글자가 와도 500이 아니라 401"""
    # hmac.compare_digest는 문자열에 비ASCII 글자가 있으면 TypeError → 전에는 500이 났음
    no_header = TestClient(app, raise_server_exceptions=False)
    response = no_header.post(
        "/calculate", json=BODY, headers=[(b"X-Calc-Token", b"\xe9abc")]
    )
    assert response.status_code == 401
    assert response.json()["error"] == "UNAUTHORIZED"


def test_rejects_all_requests_when_server_token_not_set(client, monkeypatch):
    """서버에 토큰이 설정되지 않으면 모두 막는다"""
    monkeypatch.delenv("CALC_API_TOKEN")
    response = client.post("/calculate", json=BODY)
    assert response.status_code == 503
    assert response.json()["error"] == "TOKEN_NOT_SET"


def test_diagnoses_benefits(client):
    """혜택 진단"""
    body = client.post(
        "/benefit",
        json={
            "taxYear": 2026,
            "totalSalary": 30_000_000,
            "birthDate": "1995-05-10",
            "hireDate": "2023-03-01",
            "companySize": "SME",
            "homelessHeadYn": "Y",
        },
    ).json()
    assert [r["benefitCode"] for r in body["results"]] == [
        "SME_YOUTH",
        "RENT",
        "HOUSING_SAVING",
    ]


def test_unknown_path_returns_short_message_only(client):
    """없는 주소는 짧은 안내만"""
    response = client.get("/admin")
    assert response.status_code == 404
    assert set(response.json()) == {"error", "message"}


def test_logs_wrong_token_without_token_value(client, caplog):
    """토큰이 틀리면 로그를 남기고 토큰 값은 남기지 않는다"""
    with caplog.at_level("WARNING", logger="calc"):
        client.post("/calculate", json=BODY, headers={"X-Calc-Token": "wrong-secret"})
    assert "토큰이 없거나 틀린 요청 차단" in caplog.text
    assert "wrong-secret" not in caplog.text


def test_calculation_log_does_not_contain_amounts(client, caplog):
    """계산 로그에는 금액을 남기지 않는다"""
    with caplog.at_level("INFO", logger="calc"):
        client.post("/calculate", json=BODY)
    assert "계산 완료 year=2026" in caplog.text
    assert "30000000" not in caplog.text


def test_validation_failure_logs_field_names_only(client, caplog):
    """입력값 검사 실패는 칸 이름만 남긴다"""
    with caplog.at_level("WARNING", logger="calc"):
        client.post("/calculate", json={**BODY, "totalSalary": -123456})
    assert "totalSalary" in caplog.text
    assert "-123456" not in caplog.text
