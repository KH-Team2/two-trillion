package com.hoperefund.member.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.member.dto.EmailVerificationConfirmRequestDTO;
import com.hoperefund.member.dto.EmailVerificationRequestDTO;
import com.hoperefund.member.dto.EmailVerificationResponseDTO;
import com.hoperefund.member.service.EmailVerificationService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입 이메일 인증 (F-01-01). 인증번호 발송 · 확인.
 * TODO: 팀 API 명세에 없는 주소 (제안 · password-resets 구조에 맞춤) → 명세 추가 필요
 */
@RestController
@RequestMapping("/api/auth/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    // POST /api/auth/email-verifications — 인증번호 발송. 응답의 verificationId 를 확인 · 가입 때 다시 보낸다
    @PostMapping
    public ApiResponse<EmailVerificationResponseDTO> send(
            @Valid @RequestBody EmailVerificationRequestDTO request) {
        return ApiResponse.ok(emailVerificationService.send(request.getEmail()));
    }

    // POST /api/auth/email-verifications/{verificationId}/confirmation — 인증번호 확인
    @PostMapping("/{verificationId}/confirmation")
    public ApiResponse<Map<String, Object>> confirm(
            @PathVariable String verificationId,
            @Valid @RequestBody EmailVerificationConfirmRequestDTO request) {
        emailVerificationService.confirm(verificationId, request.getCode()); // 틀리면 예외 → 400
        return ApiResponse.ok(Map.of("verified", true, "message", "이메일 인증이 완료됐어요"));
    }
}
