package com.hoperefund.income.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.service.IncomeService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소득 입력 (S-02 · F-02-01). TODO(로그인 연결 후): 회원번호를 X-Member-Id 헤더 대신 로그인 정보(Spring Security)에서 꺼내기 (회원
 * 관리 담당과 맞추기)
 */
@RestController
@RequestMapping("/api/incomes")
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    /** 조회: GET /api/incomes/2026 */
    @GetMapping("/{taxYear}")
    public ApiResponse<IncomeResponseDTO> get(
            @RequestHeader("X-Member-Id") long memberId, @PathVariable("taxYear") int taxYear) {
        return ApiResponse.ok(incomeService.get(memberId, taxYear));
    }

    /** 저장(없으면 새로, 있으면 수정): PUT /api/incomes/2026 */
    @PutMapping("/{taxYear}")
    public ApiResponse<IncomeResponseDTO> save(
            @RequestHeader("X-Member-Id") long memberId,
            @PathVariable("taxYear") int taxYear,
            @Valid @RequestBody IncomeRequestDTO req) {
        return ApiResponse.ok(incomeService.save(memberId, taxYear, req));
    }
}
