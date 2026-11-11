package com.hoperefund.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API 공통 응답 봉투 (팀 합의 11/09 · 선생님 샘플과 같은 모양)
 *
 * <pre>
 * 성공(단건): {"success": true,  "data": {...}, "meta": null, "error": null}
 * 성공(목록): {"success": true,  "data": [...], "meta": {"page":1,"size":20,"total":135,"totalPages":7}, "error": null}
 * 실패:       {"success": false, "data": null,  "meta": null, "error": {"code":"NOT_FOUND","message":"..."}}
 * 서버 오류:  {"success": false, "data": null,  "meta": null, "error": {"code":"SERVER_ERROR","message":"...","errorId":"a3f9c2d1"}}
 * </pre>
 *
 * Controller는 {@code ApiResponse.ok(결과)}로 감싸서 돌려주고, 실패는 GlobalExceptionHandler가 {@code fail(...)}로
 * 만든다.
 */
public record ApiResponse<T>(boolean success, T data, PageMeta meta, ErrorInfo error) {

    /** 단건 조회 · 저장 성공 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /** 목록 조회 성공 (페이지 정보 포함) */
    public static <T> ApiResponse<T> ok(T data, PageMeta meta) {
        return new ApiResponse<>(true, data, meta, null);
    }

    /** 실패 (사용자 요청 오류 · 400 · 401 · 404 · 409 등) */
    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, null, new ErrorInfo(code, message, null));
    }

    /** 서버 쪽 오류 (500 · 503) · errorId는 서버 로그에도 같은 번호로 남겨 원인을 찾는 데 쓴다 */
    public static ApiResponse<Void> fail(String code, String message, String errorId) {
        return new ApiResponse<>(false, null, null, new ErrorInfo(code, message, errorId));
    }

    /**
     * 실패 이유. code는 화면이 분기하는 값(VALIDATION · NOT_FOUND ...), message는 화면에 그대로 보여 줄 문구. errorId는 서버
     * 오류(500 · 503)에만 있고, 없으면 응답에서 빠진다.
     */
    public record ErrorInfo(
            String code,
            String message,
            @JsonInclude(JsonInclude.Include.NON_NULL) String errorId) {}
}
