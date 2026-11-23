import os

from fastapi.testclient import TestClient

from app.main import app

TEST_TOKEN = "test-chatbot-token"
os.environ["CHATBOT_API_TOKEN"] = TEST_TOKEN

client = TestClient(app)

AUTH_HEADERS = {"X-Chatbot-Token": TEST_TOKEN}


def test_categories():
    response = client.get("/faq/categories", headers=AUTH_HEADERS)

    assert response.status_code == 200
    assert len(response.json()["categories"]) == 6


def test_list():
    response = client.get("/faq", headers=AUTH_HEADERS)

    assert response.status_code == 200
    assert response.json()["total"] == 36
    assert "answer" not in response.json()["items"][0]


def test_detail():
    response = client.get("/faq/TAX_010", headers=AUTH_HEADERS)

    assert response.status_code == 200
    assert response.json()["id"] == "TAX_010"
    assert response.json()["answer"]


def test_not_found():
    response = client.get("/faq/TAX_999", headers=AUTH_HEADERS)

    assert response.status_code == 404


def test_filter():
    response = client.get(
        "/faq",
        params={"category": "카드·소비"},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 200
    assert response.json()["total"] == 6


def test_faq_without_token_returns_401():
    response = client.get("/faq/categories")

    assert response.status_code == 401
    assert response.json()["detail"] == "UNAUTHORIZED"


def test_faq_with_wrong_token_returns_401():
    response = client.get(
        "/faq/categories",
        headers={"X-Chatbot-Token": "wrong-token"},
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "UNAUTHORIZED"
