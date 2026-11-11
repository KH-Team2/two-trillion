package com.hoperefund.member.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.member.dto.EmailVerificationConfirmRequestDTO;
import com.hoperefund.member.dto.EmailVerificationRequestDTO;
import com.hoperefund.member.dto.EmailVerificationResponseDTO;
import com.hoperefund.member.service.EmailVerificationService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입 이메일 인증 API Controller (F-01-01)
 * 인증번호 발송 · 확인 요청을 받아 EmailVerificationService에 맡긴다.
 * 응답: 성공은 모두 200 + ApiResponse 봉투 (docs/api-rules.md · 11/09 팀 합의)
 * TODO: 팀 API 명세에 없는 주소 (제안 · password-resets 구조에 맞춤) → 명세 추가 필요
 */
@RestController
@RequestMapping("/api/auth/email-verifications")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    // 인증번호 발송: 성공 시 200 {success, data: {verificationId, expiresAt, devCode}}
    @PostMapping
    public ApiResponse<EmailVerificationResponseDTO> send(
            @Valid @RequestBody EmailVerificationRequestDTO request) {

        return ApiResponse.ok(emailVerificationService.send(request.email()));
    }

    // 인증번호 확인: 성공 시 200 {success, data: {verified: true, message}}
    @PostMapping("/{verificationId}/confirmation")
    public ApiResponse<Map<String, Object>> confirm(
            @PathVariable String verificationId,
            @Valid @RequestBody EmailVerificationConfirmRequestDTO request) {

        emailVerificationService.confirm(verificationId, request.code());

        return ApiResponse.ok(Map.of("verified", true, "message", "이메일 인증이 완료됐어요"));
    }
}
