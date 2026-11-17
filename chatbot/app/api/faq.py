import json
from pathlib import Path
from fastapi import APIRouter, HTTPException, Query

router = APIRouter(prefix='/faq', tags=['FAQ'])
DATA_PATH = Path(__file__).resolve().parents[1] / 'data' / 'tax_terms.json'


def load_terms():
    with DATA_PATH.open(encoding='utf-8') as file:
        return json.load(file)


@router.get('/categories')
def get_categories():
    return {'categories': list(dict.fromkeys(item['category'] for item in load_terms()))}


@router.get('')
def list_faq(category: str | None = Query(default=None)):
    terms = load_terms()
    if category is not None:
        terms = [item for item in terms if item['category'] == category]
    return {'total': len(terms), 'items': [
        {key: item[key] for key in ('id', 'category', 'question', 'aliases')}
        for item in terms
    ]}


@router.get('/{faq_id}')
def get_faq(faq_id: str):
    for item in load_terms():
        if item['id'] == faq_id:
            return item
    raise HTTPException(status_code=404, detail='FAQ를 찾을 수 없습니다.')
