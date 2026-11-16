package com.hoperefund.prediction.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.SimulationRequestDTO;
import com.hoperefund.prediction.service.CalculationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조건 변경 시뮬레이션 API (F-04-01) · 바꾼 값으로 다시 계산해서 시나리오로 저장해요. INCOME은 덮어쓰지 않아요.
 *
 * <p>TODO(로그인 연결 후): 회원번호를 X-Member-Id 헤더 대신 로그인 정보에서 꺼내기 (CalculationController와 같은 개발용 방식)
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
            @RequestHeader("X-Member-Id") long memberId,
            @PathVariable("taxYear") int taxYear,
            @Valid @RequestBody SimulationRequestDTO request) {
        CalculationResponseDTO result = calculationService.simulate(memberId, taxYear, request);
        return ResponseEntity.ok(ApiResponse.ok(result)); // 200 OK + 공통 봉투 JSON
    }
}
