"""
    POST /card/parse · 카드 파일 해석 + 지출 분류 (F-07 · F-08)

    [ 흐름 ]
    1. 백엔드(Spring)가 사용자가 올린 파일을 그대로 보내요 (multipart)
    2. parser.parse_file()  : 파일 → 공통 포맷 행 목록 (승인일시 · 가맹점명 · 이용금액 · 사용구분)
    3. matcher.classify()   : 행마다 가맹점명으로 분류 (5개 분류 + 일치한 목록 번호)
    4. 결과를 JSON으로 돌려줘요 · DB에 저장하지 않아요 (저장은 백엔드 CardUploadService)

    [ 오류 ]
    - 파일 문제 → 400 {"error": 코드, "message": 문구, "errorRows": [행 번호]}  (main.py에서 처리)
    - 토큰 없음/틀림 → 401 · 토큰 설정 안 됨 → 503  (deps.check_token)
"""

import logging
import time
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, UploadFile

from app.api.deps import check_token
from app.card.matcher import MerchantMatcher
from app.card.parser import parse_file
from app.schemas.card import CardParseResponse, CardRowResponse

# dependencies=[Depends(check_token)]: 이 라우터의 모든 API는 실행 전에 토큰부터 검사해요
router = APIRouter(dependencies=[Depends(check_token)])
log = logging.getLogger("calc")

# 분류기(목록 약 8만 행)를 담아 둘 변수 · 처음 한 번만 DB에서 읽고 계속 재사용해요
_matcher: MerchantMatcher | None = None


def get_matcher() -> MerchantMatcher:
    """
        분류기 꺼내기 (deps.get_criteria와 같은 방식)
        - 처음 부를 때만 DB에서 목록을 읽어서 만들고, 그다음부터는 만들어 둔 걸 돌려줘요
        - 테스트에서는 app.dependency_overrides로 가짜 분류기를 끼워 넣어요 (DB 없이 테스트)
        - 목록(MERCHANT_LIST)을 고친 뒤에는 계산 서버를 다시 켜야 반영돼요
    """
    global _matcher  # 함수 밖(파일 맨 위)의 _matcher를 바꾸겠다는 표시
    if _matcher is None:
        # import를 함수 안에 둔 이유: 테스트처럼 DB를 안 쓸 때는 oracledb를 불러오지 않으려고
        from app.card.merchant_repo import load_matcher
        from app.db.oracle import get_pool

        started = time.perf_counter()
        # acquire(): 연결 묶음(pool)에서 연결 하나 빌리기 · with가 끝나면 자동 반납
        with get_pool().acquire() as conn:
            _matcher = load_matcher(conn)
        log.info(
            "분류 목록 읽기 완료 exact=%s contains=%s %.0fms",
            len(_matcher.exact),
            len(_matcher.contains),
            (time.perf_counter() - started) * 1000,
        )
    return _matcher


@router.post(
    "/card/parse", response_model=CardParseResponse, response_model_by_alias=True
)
async def parse_card_api(
    # UploadFile: 올라온 파일 · File(): multipart의 "file" 칸에서 꺼내기 (없으면 400 VALIDATION)
    file: Annotated[UploadFile, File()],
    matcher: Annotated[MerchantMatcher, Depends(get_matcher)],
    # Form(alias="payMethod"): multipart의 "payMethod" 글자 칸 · 안 보내면 None
    pay_method: Annotated[str | None, Form(alias="payMethod")] = None,
) -> CardParseResponse:
    started = time.perf_counter()
    content = await file.read()  # 파일 내용 전체를 바이트로 읽기 (async라 await 필요)

    # 2. 파일 해석 · 문제가 있으면 CardParseError가 나고 main.py가 400으로 바꿔요
    parsed = parse_file(content, file.filename or "", pay_method)

    # 3. 행마다 분류해서 응답 모양으로 바꾸기
    rows = []
    for row in parsed:
        category_code, list_id = matcher.classify(row["merchantName"])
        rows.append(
            CardRowResponse(
                row_no=row["rowNo"],
                used_date=row["usedDate"],
                merchant_name=row["merchantName"],
                pay_method=row["payMethod"],
                amount=row["amount"],
                category_code=category_code,
                list_id=list_id,
            )
        )

    # 로그 규칙: 건수 · 처리 시간만 남기고 가맹점명 · 금액 · 파일 이름은 남기지 않아요 (개인정보)
    log.info(
        "카드 파일 해석 완료 rows=%s %.0fms",
        len(rows),
        (time.perf_counter() - started) * 1000,
    )
    return CardParseResponse(row_count=len(rows), rows=rows)