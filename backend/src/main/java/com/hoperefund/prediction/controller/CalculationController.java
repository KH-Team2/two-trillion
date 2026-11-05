package com.hoperefund.prediction.controller;

import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.service.CalculationService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계산 요청 API (F-03-01) · 계산은 CALC_MODE에 따라 파이썬 계산 서버(http) 또는 가짜 계산(mock)이 해요 @RestController
 * = @Controller + @ResponseBody → 화면 이름 대신 객체를 JSON으로 바꿔 응답해요
 *
 * <p>TODO(로그인 연결 후): 회원번호를 X-Member-Id 헤더 대신 로그인 정보(Spring Security)에서 꺼내기 지금 헤더 방식은 개발용이에요. 헤더 값만
 * 바꾸면 다른 회원 결과를 볼 수 있어서 실제 서비스에 쓰면 안 돼요.
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
    public ResponseEntity<CalculationResponseDTO> calculate(
            @RequestHeader("X-Member-Id") long memberId, @PathVariable("taxYear") int taxYear) {
        CalculationResponseDTO result = calculationService.calculate(memberId, taxYear);
        return ResponseEntity.ok(result); // 200 OK + JSON
    }
}
