package com.hoperefund.prediction.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.SimulationRequestDTO;
import com.hoperefund.prediction.service.CalculationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조건 변경 시뮬레이션 API (F-04-01) · 바꾼 값으로 다시 계산해서 시나리오로 저장해요. INCOME은 덮어쓰지 않아요.
 *
 * <p>회원번호는 로그인 토큰(JWT)에서 꺼내요 (@AuthenticationPrincipal · [수정 11/17] X-Member-Id 헤더에서 변경)
 */
@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private final CalculationService calculationService;

    public SimulationController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    /**
     * 시뮬레이션: POST /api/simulations/2026 · 요청 본문에 기준 결과 번호 · 시나리오 이름 · 입력값 전부를 받아요.
     *
     * <p>@Valid: SimulationRequestDTO의 검사(@NotNull 등)를 실행해요. 어긋나면 Service까지 오지 않고 400으로 돌려보내요.
     */
    @PostMapping("/{taxYear}")
    public ResponseEntity<ApiResponse<CalculationResponseDTO>> simulate(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("taxYear") int taxYear,
            @Valid @RequestBody SimulationRequestDTO request) {
        CalculationResponseDTO result = calculationService.simulate(memberId, taxYear, request);
        return ResponseEntity.ok(ApiResponse.ok(result)); // 200 OK + 공통 봉투 JSON
    }
}
