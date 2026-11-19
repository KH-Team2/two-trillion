package com.hoperefund.prediction.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.prediction.dto.BenefitResponseDTO;
import com.hoperefund.prediction.service.BenefitService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 청년 혜택 자격 진단 API (F-03-02 · 분담표 F-12 · S-05)
 *
 * <p>회원번호는 로그인 토큰(JWT)에서 꺼내요 (@AuthenticationPrincipal · [수정 11/17] X-Member-Id 헤더에서 변경)
 */
@RestController
@RequestMapping("/api/benefits")
public class BenefitController {

    private final BenefitService benefitService;

    public BenefitController(BenefitService benefitService) {
        this.benefitService = benefitService;
    }

    /** 진단: GET /api/benefits/2026 (저장된 2026년 소득 + 회원 자격 정보로 판정 · 저장하지 않음) */
    @GetMapping("/{taxYear}")
    public ResponseEntity<ApiResponse<BenefitResponseDTO>> diagnose(
            @AuthenticationPrincipal Long memberId, @PathVariable("taxYear") int taxYear) {
        return ResponseEntity.ok(ApiResponse.ok(benefitService.diagnose(memberId, taxYear)));
    }
}
