package com.hoperefund.common.exception;

/** 찾는 데이터가 없을 때 (404). message는 화면에 그대로 보여 줄 한글 문구. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
