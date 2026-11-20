package com.hoperefund.prediction.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.PredictionSummaryDTO;
import com.hoperefund.prediction.service.CalculationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결과 이력 조회 API (F-04-02) · 현재(기준 결과)와 시나리오를 함께 보여 주고, 하나를 골라 다시 열어요.
 *
 * <p>회원번호는 로그인 토큰(JWT)에서 꺼내요 (@AuthenticationPrincipal · [수정 11/17] X-Member-Id 헤더에서 변경)
 */
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final CalculationService calculationService;

    public PredictionController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    /** 목록: GET /api/predictions?year=2026 · 최신순 · 없으면 빈 목록 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PredictionSummaryDTO>>> getHistory(
            @AuthenticationPrincipal Long memberId, @RequestParam("year") int taxYear) {
        List<PredictionSummaryDTO> result = calculationService.getHistory(memberId, taxYear);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 하나 열기: GET /api/predictions/6 · 항목별 내역 포함 · 없거나 남의 것이면 404 */
    @GetMapping("/{predictionId}")
    public ResponseEntity<ApiResponse<CalculationResponseDTO>> getOne(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("predictionId") long predictionId) {
        CalculationResponseDTO result = calculationService.getOne(memberId, predictionId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
