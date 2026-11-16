package com.hoperefund.common.exception;

/** 같은 데이터를 동시에 저장하려다 겹쳤을 때 (409) · 화면은 잠시 후 다시 저장하라고 안내해요. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
