package com.hoperefund.common.exception;

/** 입력값이 규칙에 맞지 않을 때 (400). message는 화면에 그대로 보여 줄 한글 문구. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
