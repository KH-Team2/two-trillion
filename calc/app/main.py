"""FastAPI 시작 · 계산 서버 (Spring 서버만 부름).

실행: python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000 (calc/.env를 읽음)
/docs(자동 API 문서)는 꺼 둔다 (Spring 서버만 부르는 내부 서버라 밖에 요청 모양을 보이지 않게)
오류 응답은 {"error", "message"} 모양 · 원인(스택 트레이스·DB 정보)은 서버 로그에만.
"""

import logging
import os

from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

# [수정 11/13 고은] card 추가 · 이유: 카드 파일 해석 API(POST /card/parse)를 서버에 연결하려고
from app.api import benefit, calculate, card
from app.api.health import router as health_router

# [수정 11/13 고은] CardParseError 추가 · 이유: 파일 오류를 400 응답으로 바꾸는 처리기(아래)에서 씀
from app.card.parser import CardParseError
from app.services.errors import NoCriteriaError

load_dotenv()
log = logging.getLogger("calc")
# 로그 형식: 시각 · 수준 · 내용 (uvicorn 로그 설정은 건드리지 않고 calc 로거에만)
# 규칙: 연도 · 처리 시간 · 결과 코드만 남기고 금액 · 생년월일 · 토큰은 남기지 않는다
if not log.handlers:
    _handler = logging.StreamHandler()
    _handler.setFormatter(
        logging.Formatter("%(asctime)s %(levelname)s [calc] %(message)s")
    )
    log.addHandler(_handler)
    log.setLevel(logging.INFO)
if not os.environ.get("CALC_API_TOKEN"):
    log.error(
        "CALC_API_TOKEN이 비어 있어요. .env에 넣기 전까지 모든 계산 요청을 막아요"
    )

app = FastAPI(
    title="희망환급 Calc API",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)
# 서버 기본 상태 확인 API (/health · 토큰 없이 응답)
app.include_router(health_router)
app.include_router(calculate.router)
app.include_router(benefit.router)
# [수정 11/13 고은] 카드 파일 해석 API 연결 (F-07 · F-08)
app.include_router(card.router)


def _error(status: int, error: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status, content={"error": error, "message": message}
    )


@app.exception_handler(NoCriteriaError)
def no_criteria(_: Request, e: NoCriteriaError) -> JSONResponse:
    return _error(400, "NO_CRITERIA", e.message)


# [수정 11/13 고은] 카드 파일 오류 처리기 추가
# 이유: 파일 오류는 백엔드가 화면에 보여 줄 「문제 있는 행 번호(errorRows)」까지 필요해서
#       공통 _error()({"error", "message"})에 errorRows 칸을 하나 더 붙여 보냄
#       (백엔드 CardParseClient.CalcError가 이 모양을 읽음)
@app.exception_handler(CardParseError)
def card_parse_error(_: Request, e: CardParseError) -> JSONResponse:
    # 오류 코드 · 행 수만 남기고 파일 내용은 남기지 않음
    log.warning("카드 파일 오류 %s rows=%s", e.code, len(e.error_rows))
    return JSONResponse(
        status_code=400,
        content={"error": e.code, "message": e.message, "errorRows": e.error_rows},
    )


@app.exception_handler(RequestValidationError)
def invalid(_: Request, e: RequestValidationError) -> JSONResponse:
    # 어느 칸이 틀렸는지만 남긴다 (입력한 값은 남기지 않음)
    fields = sorted(
        {".".join(str(p) for p in err.get("loc", ())) for err in e.errors()}
    )
    log.warning("입력값 검사 실패: %s", fields)
    return _error(400, "VALIDATION", "입력값을 확인해 주세요")


@app.exception_handler(HTTPException)
def http_error(_: Request, e: HTTPException) -> JSONResponse:
    if e.status_code == 401:
        return _error(401, "UNAUTHORIZED", "허용되지 않은 요청이에요")
    if e.status_code == 503:
        return _error(503, "TOKEN_NOT_SET", "계산 서버 설정이 끝나지 않았어요")
    return _error(e.status_code, "HTTP_ERROR", "요청을 처리할 수 없어요")


@app.exception_handler(Exception)
def unexpected(_: Request, e: Exception) -> JSONResponse:
    log.exception("처리하지 못한 오류")
    return _error(500, "SERVER_ERROR", "잠시 후 다시 시도해 주세요")