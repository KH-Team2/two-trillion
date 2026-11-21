package com.hoperefund.member.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.member.dto.MemberProfileRequestDTO;
import com.hoperefund.member.dto.MemberProfileResponseDTO;
import com.hoperefund.member.dto.PasswordChangeRequestDTO;
import com.hoperefund.member.service.MemberProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원정보 · 자격 정보 조회 · 수정 · 비밀번호 변경 (F-01-04). 로그인한 회원만 (SecurityConfig 의 /api/**).
 * 회원번호는 주소에 넣지 않고 로그인 토큰에서 꺼낸다 (me = 로그인한 본인).
 * TODO: 팀 API 명세에 없는 주소 (제안) → 명세 추가 필요
 */
@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberProfileController {

    private final MemberProfileService memberProfileService;

    // GET /api/members/me — 내 회원정보 (이메일은 일부를 가림)
    @GetMapping
    public ApiResponse<MemberProfileResponseDTO> getProfile(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(memberProfileService.getProfile(memberId));
    }

    // PUT /api/members/me — 성별 · 군 복무 · 입사일 · 회사 규모 · 무주택 세대주 저장 (모든 칸을 보낸다)
    @PutMapping
    public ApiResponse<MemberProfileResponseDTO> updateProfile(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody MemberProfileRequestDTO request) {
        return ApiResponse.ok(memberProfileService.updateProfile(memberId, request));
    }

    // PUT /api/members/me/password — 현재 비밀번호 확인 후 새 비밀번호 저장. 돌려줄 데이터가 없어 data 는 null
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PasswordChangeRequestDTO request) {
        memberProfileService.changePassword(memberId, request);
        return ApiResponse.ok(null);
    }
}
