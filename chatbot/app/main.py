from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.faq import router as faq_router

"""
희망환급 세법 용어 FAQ 챗봇 서버 시작 파일

TODO: FAQ 카테고리 및 질문 목록 조회 API 추가
TODO: FAQ ID 기반 고정 답변 조회 API 추가
TODO: Ollama Gemma 3 기반 자유 질문 FAQ 분류 기능 추가
TODO: Spring Boot 내부 인증 연동
"""

app = FastAPI(
    title="희망환급 Chatbot API",
    version="0.1.0",
)

app.include_router(health_router)
app.include_router(faq_router)
