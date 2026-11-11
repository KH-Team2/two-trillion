package com.hoperefund.income.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 소득 기능에서 쓰는 업무 오류 (예: 소득 정보 없음 404 · 연도 오류 400)
 *
 * <p>Service가 이 예외를 던지면(throw) IncomeExceptionHandler가 받아서 {"error": 코드, "message": 문구} 응답으로
 * 바꾼다.
 *
 * <p>RuntimeException을 상속: 메서드마다 throws를 안 써도 되는 예외 종류
 */
@Getter // status · code getter 자동 생성
public class IncomeException extends RuntimeException {

    private final HttpStatus status; // 응답 상태 코드 (예: HttpStatus.NOT_FOUND = 404)
    private final String code; // 오류 코드 (예: "NOT_FOUND") → 화면이 이 값으로 분기

    /**
     * @param status 응답 상태 코드
     * @param code 오류 코드
     * @param message 화면에 보여 줄 한글 문구
     */
    public IncomeException(HttpStatus status, String code, String message) {
        super(message); // 부모(RuntimeException)에 문구 저장 → getMessage()로 꺼냄
        this.status = status;
        this.code = code;
    }
}
