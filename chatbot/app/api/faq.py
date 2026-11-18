import json
from pathlib import Path
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field
from app.services.classifier import classify_question, ClassifierError
# FAQ 목록, 카테고리, 상세 조회 구현

router = APIRouter(prefix='/faq', tags=['FAQ'])
DATA_PATH = Path(__file__).resolve().parents[1] / 'data' / 'tax_terms.json'


def load_terms():
    with DATA_PATH.open(encoding='utf-8') as file:
        return json.load(file)

# 카테고리 목록
@router.get('/categories')
def get_categories():
    return {'categories': list(dict.fromkeys(item['category'] for item in load_terms()))}

# 전체 FAQ 목록, 선택적 카테고리 필터링
@router.get('')
def list_faq(category: str | None = Query(default=None)):
    terms = load_terms()
    if category is not None:
        terms = [item for item in terms if item['category'] == category]
    return {'total': len(terms), 'items': [
        {key: item[key] for key in ('id', 'category', 'term', 'aliases')}
        for item in terms
    ]}

# 특정 FAQ 상세 조회
@router.get('/{faq_id}')
def get_faq(faq_id: str):
    for item in load_terms():
        if item['id'] == faq_id:
            return item
    raise HTTPException(status_code=404, detail='FAQ를 찾을 수 없습니다.')

# 사용자 질문 데이터 형식
class FaqAskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=200)


# Gemma 3 질문 분류 API
@router.post('/ask')
def ask_faq(request: FaqAskRequest):
    question = request.question.strip()
    if not question:
        raise HTTPException(status_code=422, detail='질문을 입력해 주세요.')
    terms = load_terms()
    try:
        faq_id = classify_question(question, terms)
    except ClassifierError as exc:
        raise HTTPException(status_code=503, detail='AI 질문 분류 서비스를 이용할 수 없습니다.') from exc
    if faq_id is None:
        return {'matched': False, 'faq_id': None, 'faq': None}
    faq = next((item for item in terms if item['id'] == faq_id), None)
    return {'matched': faq is not None, 'faq_id': faq_id if faq is not None else None, 'faq': faq}