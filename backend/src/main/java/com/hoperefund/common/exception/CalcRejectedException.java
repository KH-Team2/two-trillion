package com.hoperefund.common.exception;

/**
 * 계산 서버가 입력값을 거부했을 때 (400 CALC_REJECTED) 서버 장애(503 CALC_UNAVAILABLE)와 구분해요 → 다시 시도해도 같은 결과라 「입력값
 * 확인」 안내가 맞아요.
 */
public class CalcRejectedException extends RuntimeException {
    public CalcRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
