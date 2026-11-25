package com.hoperefund.chatbot.controller;

import com.hoperefund.chatbot.service.GuideService;
import com.hoperefund.common.dto.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 계산 결과별 맞춤 절세 가이드 생성 및 재사용 API
@RestController
@RequestMapping("/api/predictions")
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    // 최초 요청 시 생성·저장, 기존 결과가 있으면 재사용
    @PostMapping("/{predictionId}/recommendations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generate(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("predictionId") long predictionId) {

        Map<String, Object> result = guideService.generate(memberId, predictionId);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // 이미 DB에 저장된 맞춤 절세 가이드만 조회
    @GetMapping("/{predictionId}/recommendations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSaved(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("predictionId") long predictionId) {

        Map<String, Object> result = guideService.getSaved(memberId, predictionId);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
