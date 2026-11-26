package com.hoperefund.admin.controller;

import com.hoperefund.admin.dto.TaxCriteriaRequestDTO;
import com.hoperefund.admin.dto.TaxCriteriaResponseDTO;
import com.hoperefund.admin.service.AdminTaxCriteriaService;
import com.hoperefund.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/*
 * F-19 (F-08-01): 관리자 세법 기준값 관리 컨트롤러
 * - DEDUCTION_TYPE / TAX_BRACKET / BENEFIT_CONDITION 조회·수정 및 신규 귀속 연도 추가
 * - 수정일(UPDATED_AT) 및 수정자(UPDATED_BY) 기록
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
            @RequestParam(value = "taxYear", defaultValue = "2026") Integer taxYear) {
        return ApiResponse.ok(adminTaxCriteriaService.getTaxCriteria(taxYear));
    }

    /*
     * F-19: 신규 귀속 연도 기준값 생성 API (기존 연도 또는 입력값 기반 추가)
     * URI: POST /api/admin/tax-criteria
     */
    @PostMapping
    public ApiResponse<Void> createTaxCriteria(
            @Valid @RequestBody TaxCriteriaRequestDTO dto,
            @AuthenticationPrincipal Long currentAdminId) {
        adminTaxCriteriaService.createTaxCriteria(dto, currentAdminId);
        return ApiResponse.ok(null);
    }

    /*
     * F-19: 기존 세법 기준값 수정 API
     * URI: PUT /api/admin/tax-criteria
     */
    @PutMapping
    public ApiResponse<Void> updateTaxCriteria(
            @Valid @RequestBody TaxCriteriaRequestDTO dto,
            @AuthenticationPrincipal Long currentAdminId) {
        adminTaxCriteriaService.updateTaxCriteria(dto, currentAdminId);
        return ApiResponse.ok(null);
    }
}
