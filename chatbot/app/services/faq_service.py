import re

from fastapi import HTTPException

from app.repositories.faq_repository import (
    find_faq,
    get_categories,
    list_faqs,
    load_terms,
)
from app.services.classifier import ClassifierError, classify_question
from app.services.privacy_filter import check_privacy


def get_faq_categories() -> dict:
    """FAQ 카테고리 목록을 반환한다."""
    return {"categories": get_categories()}


def get_faq_list(category: str | None = None) -> dict:
    """FAQ 목록을 반환한다."""
    items = list_faqs(category)

    return {
        "total": len(items),
        "items": items,
    }


def get_faq_detail(faq_id: str) -> dict:
    """FAQ 상세 정보를 반환한다."""
    faq = find_faq(faq_id)

    if faq is None:
        raise HTTPException(
            status_code=404,
            detail="FAQ를 찾을 수 없습니다.",
        )

    return faq


def match_faq(question: str) -> dict:
    """자유 질문을 기존 FAQ에 매칭한다."""
    question = question.strip()

    if not question:
        raise HTTPException(
            status_code=422,
            detail="질문을 입력해 주세요.",
        )

    if check_privacy(question):
        raise HTTPException(
            status_code=400,
            detail="개인정보가 포함되어 있어 질문을 전송할 수 없습니다.",
        )

    if re.search(r"[0-9]", question):
        raise HTTPException(
            status_code=400,
            detail=(
                "세금 용어 설명만 제공하므로 개인정보의 위험이 있는 "
                "숫자가 포함된 질문은 입력할 수 없습니다."
            ),
        )

    terms = load_terms()

    try:
        faq_id = classify_question(question, terms)
    except ClassifierError as exc:
        raise HTTPException(
            status_code=503,
            detail="AI 질문 분류 서비스를 이용할 수 없습니다.",
        ) from exc

    if faq_id is None:
        return {
            "matched": False,
            "faqId": None,
            "faq": None,
        }

    faq = find_faq(faq_id)

    return {
        "matched": faq is not None,
        "faqId": faq_id if faq is not None else None,
        "faq": faq,
    }
