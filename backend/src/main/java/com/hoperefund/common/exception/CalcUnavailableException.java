package com.hoperefund.common.exception;

/** 계산 서버(파이썬)에 연결할 수 없을 때 (503) · 원인은 로그에만 남겨요. */
public class CalcUnavailableException extends RuntimeException {
    public CalcUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
