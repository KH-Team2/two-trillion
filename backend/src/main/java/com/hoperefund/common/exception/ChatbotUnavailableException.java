package com.hoperefund.common.exception;

/** 챗봇 서버 또는 AI 질문 분류 서비스를 이용할 수 없을 때 (503). */
public class ChatbotUnavailableException extends RuntimeException {

    public ChatbotUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}