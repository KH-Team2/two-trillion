package com.hoperefund.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Service 에서 "요청은 왔지만 업무 규칙상 처리할 수 없다"를 알릴 때 던지는 예외.
 * GlobalExceptionHandler 가 받아서 상태 코드와 실패 봉투 {"success": false, "error": {"code", "message"}} 로 응답한다.
 * 5xx 로 던지면 문구는 숨기고 SERVER_ERROR + errorId 로 바꾼다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status; // 응답 상태 코드 (예: 409)
    private final String code; // 화면이 구분하는 오류 코드 (예: DUPLICATE_LOGIN_ID)

    public BusinessException(HttpStatus status, String code, String message) {
        super(message); // 화면에 그대로 보여 줄 문구
        this.status = status;
        this.code = code;
    }
}
