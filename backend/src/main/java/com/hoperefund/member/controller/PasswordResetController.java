package com.hoperefund.member.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.member.dto.LoginIdLookupResponseDTO;
import com.hoperefund.member.dto.MemberEmailRequestDTO;
import com.hoperefund.member.dto.PasswordResetCompleteRequestDTO;
import com.hoperefund.member.dto.PasswordResetResponseDTO;
import com.hoperefund.member.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아이디 찾기 · 비밀번호 재설정 (F-01-03). 로그인 전에 부르는 주소라 /api/auth/** 아래에 둔다.
 * 이메일은 주소(쿼리)에 넣으면 서버 · 프록시 접속 기록에 남아서 모두 POST 본문으로 받는다.
 * TODO: 팀 API 명세에 없는 주소 (제안) → 명세 추가 필요
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // POST /api/auth/login-id-lookups — 아이디 찾기. 일부를 가린 아이디(예: te******)
    @PostMapping("/login-id-lookups")
    public ApiResponse<LoginIdLookupResponseDTO> findLoginId(
            @Valid @RequestBody MemberEmailRequestDTO request) {
        return ApiResponse.ok(passwordResetService.findLoginId(request.getEmail()));
    }

    // POST /api/auth/password-resets — 재설정 인증 코드 메일 발송 (10분 유효)
    @PostMapping("/password-resets")
    public ApiResponse<PasswordResetResponseDTO> sendCode(
            @Valid @RequestBody MemberEmailRequestDTO request) {
        return ApiResponse.ok(passwordResetService.sendCode(request.getEmail()));
    }

    // POST /api/auth/password-resets/completion — 코드 확인 · 새 비밀번호 저장. 돌려줄 데이터가 없어 data 는 null
    @PostMapping("/password-resets/completion")
    public ApiResponse<Void> complete(@Valid @RequestBody PasswordResetCompleteRequestDTO request) {
        passwordResetService.complete(request);
        return ApiResponse.ok(null);
    }
}
