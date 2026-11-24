import logging
import os
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI

from app.api.faq import router as faq_router
from app.api.health import router as health_router

# chatbot/.env 파일에서 환경변수를 읽는다.
load_dotenv()

log = logging.getLogger("chatbot")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """내부 인증 토큰이 없으면 서버 시작을 중단한다."""
    if not os.environ.get("CHATBOT_API_TOKEN"):
        log.error("CHATBOT_API_TOKEN이 설정되지 않았습니다.")
        raise RuntimeError("CHATBOT_API_TOKEN is not set")
    yield


app = FastAPI(
    title="희망환급 Chatbot API",
    version="0.1.0",
    lifespan=lifespan,
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)

# 상태 확인은 인증 없이 허용
app.include_router(health_router)

# FAQ 관련 API는 내부 인증 필수
app.include_router(faq_router)
