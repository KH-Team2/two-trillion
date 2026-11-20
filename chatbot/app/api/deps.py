import hmac
import logging
import os

from fastapi import Header, HTTPException, Request

log = logging.getLogger("chatbot")

def check_token(request: Request, x_chatbot_token: str | None = Header(default=None),) -> None:
    """Spring Boot에서 보낸 내부 인증 토큰을 확인한다."""

    expected = os.environ.get("CHATBOT_API_TOKEN")

    # 서버 설정에 토큰이 없으면 요청을 거부한다.
    if not expected:
        log.error("CHATBOT_API_TOKEN 환경변수가 설정되지 않았습니다.")
        raise HTTPException(
            status_code=503,
            detail="TOKEN_NOT_SET",
        )

    # 전달받은 토큰과 설정된 토큰을 안전하게 비교한다.
    sent = (x_chatbot_token or "").encode("utf-8", "surrogateescape")

    if not (
        x_chatbot_token
        and hmac.compare_digest(sent, expected.encode("utf-8"))
    ):
        log.warning(
            "인증되지 않은 챗봇 요청: %s %s",
            request.method,
            request.url.path,
        )
        raise HTTPException(
            status_code=401,
            detail="UNAUTHORIZED",
        )