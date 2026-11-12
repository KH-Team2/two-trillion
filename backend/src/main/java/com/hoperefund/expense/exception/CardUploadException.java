package com.hoperefund.expense.exception;

import lombok.Getter;

/**
 * 카드 업로드에서만 쓰는 오류 (오류 코드를 그대로 화면에 넘겨야 해서 따로 만듦)
 *
 * <p>공통 BadRequestException은 코드가 항상 VALIDATION이라, API 명세서의 업로드 전용 코드
 * (UNSUPPORTED_FORMAT · PAY_METHOD_REQUIRED · ROW_LIMIT · DUPLICATE_WARNING)를 보낼 수 없다.
 *
 * <ul>
 *   <li>status: 응답 상태 번호 (400 · 409)
 *   <li>code: 화면이 분기하는 오류 코드 (예: PAY_METHOD_REQUIRED → 신용/체크 선택 창)
 *   <li>message: 화면에 그대로 보여 줄 한글 문구
 * </ul>
 */
@Getter // status · code의 getter 자동 생성 (message는 RuntimeException의 getMessage() 사용)
public class CardUploadException extends RuntimeException {

    private final int status;
    private final String code;

    public CardUploadException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** 400 오류 만들기 (예: badRequest("UNSUPPORTED_FORMAT", "아직 지원하지 않는 양식이에요.")) */
    public static CardUploadException badRequest(String code, String message) {
        return new CardUploadException(400, code, message);
    }
}
