package com.hoperefund.common.dto;

/**
 * 목록 응답의 페이지 정보 (ApiResponse.meta). 페이지 번호는 1부터 시작한다.
 *
 * <p>예: {@code ApiResponse.ok(list, PageMeta.of(page, size, total))}
 */
public record PageMeta(int page, int size, int total, int totalPages) {

    public static PageMeta of(int page, int size, int total) {
        int totalPages = size <= 0 ? 0 : (total + size - 1) / size; // 올림 나눗셈
        return new PageMeta(page, size, total, totalPages);
    }
}
