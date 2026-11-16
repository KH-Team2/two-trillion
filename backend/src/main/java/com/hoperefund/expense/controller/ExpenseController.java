package com.hoperefund.expense.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.expense.dto.ExpenseItemDTO;
import com.hoperefund.expense.dto.ExpenseTotalDTO;
import com.hoperefund.expense.dto.UploadHistoryDTO;
import com.hoperefund.expense.service.ExpenseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 거래 내역 조회 · 삭제 API (F-09 · F-02-04)
 *
 * <ul>
 *   <li>GET /api/card-uploads : 업로드 이력
 *   <li>GET /api/expenses?year= 또는 ?uploadId= : 거래 목록 + 분류 근거
 *   <li>GET /api/expense-totals?year= : 분류 · 결제수단별 합계
 *   <li>DELETE /api/card-uploads/{uploadId} : 업로드 삭제 → 204 (응답 본문 없음)
 * </ul>
 *
 * <p>업로드(POST /api/card-uploads)는 CardUploadController에 있음 · 주소가 같아도 HTTP 방식(GET · POST · DELETE)이
 * 다르면 다른 Controller에 둘 수 있음
 *
 * <p>남의 업로드 번호 → 404 NOT_FOUND (없는 것과 같은 응답 · 팀 규칙)
 *
 * <p>회원번호: 로그인 기능이 붙기 전까지는 개발용 헤더 X-Member-Id로 받는다 (업로드 · 소득 API와 같음)
 */
@RestController
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    /** 업로드 이력 */
    @GetMapping("/api/card-uploads")
    public ApiResponse<List<UploadHistoryDTO>> getUploads(
            @RequestHeader("X-Member-Id") long memberId) {
        return ApiResponse.ok(expenseService.getUploads(memberId));
    }

    /**
     * 거래 목록 + 분류 근거
     *
     * <p>year · uploadId 둘 다 선택값(required = false · 없으면 null) · 단, 둘 다 없으면 Service가 400
     */
    @GetMapping("/api/expenses")
    public ApiResponse<List<ExpenseItemDTO>> getExpenses(
            @RequestHeader("X-Member-Id") long memberId,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "uploadId", required = false) Long uploadId) {
        return ApiResponse.ok(expenseService.getExpenses(memberId, year, uploadId));
    }

    /** 분류 · 결제수단별 합계 (year 필수 · 없으면 공통 처리기가 400) */
    @GetMapping("/api/expense-totals")
    public ApiResponse<List<ExpenseTotalDTO>> getTotals(
            @RequestHeader("X-Member-Id") long memberId, @RequestParam("year") int year) {
        return ApiResponse.ok(expenseService.getTotals(memberId, year));
    }

    /**
     * 업로드 삭제 (그 업로드의 거래도 같이 삭제)
     *
     * <p>ResponseEntity.noContent(): 204 No Content = 「성공했고, 돌려줄 내용은 없음」 (API 명세서)
     *
     * <p>@PathVariable: 주소의 {uploadId} 자리 값을 꺼냄 (예: /api/card-uploads/10 → 10)
     */
    @DeleteMapping("/api/card-uploads/{uploadId}")
    public ResponseEntity<Void> deleteUpload(
            @RequestHeader("X-Member-Id") long memberId, @PathVariable("uploadId") long uploadId) {
        expenseService.deleteUpload(memberId, uploadId);
        return ResponseEntity.noContent().build();
    }
}
