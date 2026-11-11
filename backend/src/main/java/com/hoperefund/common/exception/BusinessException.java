package com.hoperefund.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 업무 규칙 위반 예외
 * 서비스에서 발생시키면 GlobalExceptionHandler가 공통 봉투
 * {"success": false, "error": {"code", "message"}} 형식으로 응답한다 (5xx는 SERVER_ERROR + errorId).
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status; // 응답 HTTP 상태 코드
    private final String code; // 응답 오류 코드 (예: DUPLICATE_LOGIN_ID)

    public BusinessException(HttpStatus status, String code, String message) {
        super(message); // 화면에 보여줄 문구
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
