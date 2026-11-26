package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.CriteriaHistoryResponseDTO;
import com.hoperefund.admin.dto.TaxCriteriaRequestDTO;
import com.hoperefund.admin.dto.TaxCriteriaResponseDTO;
import com.hoperefund.admin.dto.TaxCriteriaYearCopyRequestDTO;
import com.hoperefund.admin.service.AdminTaxCriteriaService;
import com.hoperefund.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/*
 * F-19 (F-08-01): 관리자 세법 기준값 관리 컨트롤러
 * - DEDUCTION_TYPE / TAX_BRACKET / BENEFIT_CONDITION 조회·수정 및 신규 귀속 연도 추가
 * - 수정일(UPDATED_AT) 및 수정자(UPDATED_BY) 기록 → 이전 값 · 새 값은 DB 트리거가 CRITERIA_HISTORY에 남김 (v4.4)
 * - /api/admin/** 라서 관리자 토큰만 들어와요 (아니면 SecurityConfig가 403)
 *
 * <p>[수정 11/26 고은]
 * - 조회: taxYear 기본값 2026 삭제 → 비우면 기준값이 있는 가장 최근 연도 · 2000~2100만
 * - 수정 · 추가에 접속 IP를 받아 ACCESS_LOG에 기록 (명세 ④)
 * - 연도 복사를 POST /years로 분리 · 변경 이력 조회 GET /history 추가
 */
@RestController
@RequestMapping("/api/admin/tax-criteria")
@RequiredArgsConstructor
public class AdminTaxCriteriaController {

    private final AdminTaxCriteriaService adminTaxCriteriaService;

    /*
     * F-19: 귀속 연도별 세법 기준값(공제항목·세율구간·혜택조건) 조회 API
     * URI: GET /api/admin/tax-criteria?taxYear=2026
     */
    @GetMapping
    public ApiResponse<TaxCriteriaResponseDTO> getTaxCriteria(
            @RequestParam(value = "taxYear", required = false) @Min(2000) @Max(2100)
                    Integer taxYear) {
        return ApiResponse.ok(adminTaxCriteriaService.getTaxCriteria(taxYear));
    }

    /*
     * F-19: 기준값 행 추가 API (같은 코드로 구간을 하나 더 넣을 때)
     * URI: POST /api/admin/tax-criteria
     * [수정 11/26 고은] 연도 복사(sourceYear)는 POST /years로 분리
     */
    @PostMapping
    public ApiResponse<Void> createTaxCriteria(
            @Valid @RequestBody TaxCriteriaRequestDTO dto,
            @AuthenticationPrincipal Long currentAdminId,
            HttpServletRequest request) {
        adminTaxCriteriaService.createTaxCriteria(dto, currentAdminId, request.getRemoteAddr());
        return ApiResponse.ok(null);
    }

    /*
     * F-19: 기존 세법 기준값 수정 API (본문의 targetType + id로 행을 찾음)
     * URI: PUT /api/admin/tax-criteria
     */
    @PutMapping
    public ApiResponse<Void> updateTaxCriteria(
            @Valid @RequestBody TaxCriteriaRequestDTO dto,
            @AuthenticationPrincipal Long currentAdminId,
            HttpServletRequest request) {
        adminTaxCriteriaService.updateTaxCriteria(dto, currentAdminId, request.getRemoteAddr());
        return ApiResponse.ok(null);
    }

    /*
     * [추가 11/26 고은] 새 귀속 연도 추가 (이미 있는 연도를 통째로 복사)
     * URI: POST /api/admin/tax-criteria/years  본문 {"sourceYear":2026,"targetYear":2027}
     */
    @PostMapping("/years")
    public ApiResponse<Void> copyYear(
            @Valid @RequestBody TaxCriteriaYearCopyRequestDTO dto,
            @AuthenticationPrincipal Long currentAdminId,
            HttpServletRequest request) {
        adminTaxCriteriaService.copyYear(dto, currentAdminId, request.getRemoteAddr());
        return ApiResponse.ok(null);
    }

    /*
     * [추가 11/26 고은] 한 행의 변경 이력 (DB 트리거가 남긴 CRITERIA_HISTORY · 최신순 최대 100줄)
     * URI: GET /api/admin/tax-criteria/history?targetType=DEDUCTION_TYPE&id=12
     */
    @GetMapping("/history")
    public ApiResponse<List<CriteriaHistoryResponseDTO>> getHistory(
            @RequestParam("targetType")
                    @Pattern(regexp = "DEDUCTION_TYPE|TAX_BRACKET|BENEFIT_CONDITION")
                    String targetType,
            @RequestParam("id") @Min(1) Long id) {
        return ApiResponse.ok(adminTaxCriteriaService.getHistory(targetType, id));
    }
}
