from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from app.api.deps import check_token
from app.services.faq_service import (
    get_faq_categories,
    get_faq_detail,
    get_faq_list,
    match_faq,
)

router = APIRouter(prefix="/faq", tags=["FAQ"], dependencies=[Depends(check_token)],)

class FaqAskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=100)

@router.get("/categories")
def get_categories():
    return get_faq_categories()

@router.get("")
def list_faq(category: str | None = Query(default=None)):
    return get_faq_list(category)

@router.get("/{faq_id}")
def get_faq(faq_id: str):
    return get_faq_detail(faq_id)

@router.post("/ask")
def ask_faq(request: FaqAskRequest):
    return match_faq(request.question)