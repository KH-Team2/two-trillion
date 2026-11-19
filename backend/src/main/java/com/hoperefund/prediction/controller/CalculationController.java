package com.hoperefund.prediction.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.service.CalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계산 요청 API (F-03-01) · 계산은 CALC_MODE에 따라 파이썬 계산 서버(http) 또는 가짜 계산(mock)이 해요 @RestController
 * = @Controller + @ResponseBody → 화면 이름 대신 객체를 JSON으로 바꿔 응답해요
 *
 * <p>회원번호는 로그인 토큰(JWT)에서 꺼내요 (@AuthenticationPrincipal · [수정 11/17] X-Member-Id 헤더에서 변경)
 */
@RestController
@RequestMapping("/api/calculations")
public class CalculationController {

    private final CalculationService calculationService;

    public CalculationController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    /** 계산: POST /api/calculations/2026 저장된 2026년 소득으로 계산해요. 요청 본문(body)은 없어요. */
    @PostMapping("/{taxYear}")
    public ResponseEntity<ApiResponse<CalculationResponseDTO>> calculate(
            @AuthenticationPrincipal Long memberId, @PathVariable("taxYear") int taxYear) {
        CalculationResponseDTO result = calculationService.calculate(memberId, taxYear);
        return ResponseEntity.ok(ApiResponse.ok(result)); // 200 OK + 공통 봉투 JSON
    }

    /** 조회: GET /api/calculations/2026 저장된 2026년 계산 결과 중 가장 최근 것을 돌려줘요 (대시보드 · F-06-01) */
    @GetMapping("/{taxYear}")
    public ResponseEntity<ApiResponse<CalculationResponseDTO>> getLatest(
            @AuthenticationPrincipal Long memberId, @PathVariable("taxYear") int taxYear) {
        CalculationResponseDTO result = calculationService.getLatest(memberId, taxYear);
        return ResponseEntity.ok(ApiResponse.ok(result)); // 200 OK + 공통 봉투 JSON
    }
}
