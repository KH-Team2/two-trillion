from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.api.deps import check_token
from app.repositories.guide_repository import find_guide
from app.services.guide_service import generate_guide


router = APIRouter(
    prefix="/guide",
    tags=["Guide"],
    dependencies=[Depends(check_token)],
)


class GuideGenerateRequest(BaseModel):
    codes: list[str] = Field(min_length=1, max_length=3)


@router.post("/generate")
def generate_guides(request: GuideGenerateRequest):
    """추천 코드 목록에 대한 절세 가이드 설명을 생성한다."""

    codes = request.codes

    # 동일 코드 중복 검사
    if len(codes) != len(set(codes)):
        raise HTTPException(
            status_code=400,
            detail="중복된 추천 코드가 있습니다.",
        )

    # 지원하지 않는 코드가 있는지 먼저 검사
    if any(find_guide(code) is None for code in codes):
        raise HTTPException(
            status_code=400,
            detail="지원하지 않는 추천 코드입니다.",
        )

    items = [generate_guide(code) for code in codes]

    return {"items": items}
