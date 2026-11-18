from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_categories():
    response = client.get('/faq/categories')
    assert response.status_code == 200
    assert len(response.json()['categories']) == 6


def test_list():
    response = client.get('/faq')
    assert response.status_code == 200
    assert response.json()['total'] == 36
    assert 'answer' not in response.json()['items'][0]


def test_detail():
    response = client.get('/faq/TAX_010')
    assert response.status_code == 200
    assert response.json()['id'] == 'TAX_010'
    assert response.json()['answer']


def test_not_found():
    assert client.get('/faq/TAX_999').status_code == 404


def test_filter():
    response = client.get('/faq', params={'category': '카드·소비'})
    assert response.status_code == 200
    assert response.json()['total'] == 6
