package com.hoperefund.common.exception;

/**
 * 해당 연도 기준값(DEDUCTION_TYPE · TAX_BRACKET)이 없을 때 (400) 응답의 error 값이 "NO_CRITERIA"로 나가요 (노션 「동석님 전달」에
 * 넘긴 오류 모양). BadRequestException을 상속해서, 이 예외 전용 처리가 없으면 일반 400으로 처리돼요.
 */
public class NoCriteriaException extends BadRequestException {
    public NoCriteriaException() {
        super("해당 연도 기준값이 없어요");
    }
}
