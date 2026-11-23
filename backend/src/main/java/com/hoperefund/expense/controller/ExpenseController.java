package com.hoperefund.expense.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.expense.dto.ExpenseItemDTO;
import com.hoperefund.expense.dto.ExpenseTotalDTO;
import com.hoperefund.expense.dto.UploadHistoryDTO;
import com.hoperefund.expense.service.ExpenseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 거래 내역 조회 · 삭제 API (F-09 · F-02-04)
 *
 * <ul>
 *   <li>GET /api/card-uploads : 업로드 이력
 *   <li>GET /api/expenses?year= 또는 ?uploadId= : 거래 목록 + 분류 근거
 *   <li>GET /api/expense-totals?year= : 분류 · 결제수단별 합계
 *   <li>DELETE /api/card-uploads/{uploadId} : 업로드 삭제 → 200 {success: true, data: null}
 * </ul>
 *
 * <p>업로드(POST /api/card-uploads)는 CardUploadController에 있음 · 주소가 같아도 HTTP 방식(GET · POST · DELETE)이
 * 다르면 다른 Controller에 둘 수 있음
 *
 * <p>남의 업로드 번호 → 404 NOT_FOUND (없는 것과 같은 응답 · 팀 규칙)
 *
 * <p>회원번호: 로그인 토큰(JWT)에서 꺼낸다 (@AuthenticationPrincipal · 주소 · 헤더로 받지 않음)
 *
 * <p>[수정 11/19 고은] X-Member-Id 헤더 → 로그인 토큰 · 이유: 헤더 숫자만 바꾸면 남의 거래를 보고 지울 수 있었음 (회원
 * 기능 F-01-02 · 소득 · 계산 API와 같은 방식)
 */
@RestController
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    /** 업로드 이력 */
    @GetMapping("/api/card-uploads")
    public ApiResponse<List<UploadHistoryDTO>> getUploads(
            // [수정 11/19 고은] 로그인 토큰에서 꺼낸 회원번호 (SecurityConfig의 JWT 필터가 넣어 줌)
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(expenseService.getUploads(memberId));
    }

    /**
     * 거래 목록 + 분류 근거
     *
     * <p>year · uploadId 둘 다 선택값(required = false · 없으면 null) · 단, 둘 다 없으면 Service가 400
     */
    @GetMapping("/api/expenses")
    public ApiResponse<List<ExpenseItemDTO>> getExpenses(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "uploadId", required = false) Long uploadId) {
        return ApiResponse.ok(expenseService.getExpenses(memberId, year, uploadId));
    }

    /** 분류 · 결제수단별 합계 (year 필수 · 없으면 공통 처리기가 400) */
    @GetMapping("/api/expense-totals")
    public ApiResponse<List<ExpenseTotalDTO>> getTotals(
            @AuthenticationPrincipal Long memberId, @RequestParam("year") int year) {
        return ApiResponse.ok(expenseService.getTotals(memberId, year));
    }

    /**
     * 업로드 삭제 (그 업로드의 거래도 같이 삭제)
     *
     * <p>[수정 11/23 고은] 204 → 200 + 공통 봉투 {success: true, data: null}
     *
     * <p>이유: 팀 API 규칙(api-rules.md 3번) 「성공 응답은 모두 200 · 201 · 204 안 씀」에 맞춤 · 돌려줄 데이터가 없으면 data를
     * null로 보냄
     *
     * <p>@PathVariable: 주소의 {uploadId} 자리 값을 꺼냄 (예: /api/card-uploads/10 → 10)
     */
    @DeleteMapping("/api/card-uploads/{uploadId}")
    public ApiResponse<Void> deleteUpload(
            @AuthenticationPrincipal Long memberId, @PathVariable("uploadId") long uploadId) {
        expenseService.deleteUpload(memberId, uploadId);
        // ApiResponse<Void>: 돌려줄 데이터가 없다는 표시 (Void = 「값 없음」 타입) → JSON의 data는 null
        return ApiResponse.ok(null);
    }
}
