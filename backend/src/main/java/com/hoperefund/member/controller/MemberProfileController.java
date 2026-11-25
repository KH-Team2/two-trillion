package com.hoperefund.member.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.common.service.TokenService.TokenInfo;
import com.hoperefund.member.dto.MemberProfileRequestDTO;
import com.hoperefund.member.dto.MemberProfileResponseDTO;
import com.hoperefund.member.dto.MemberWithdrawalRequestDTO;
import com.hoperefund.member.dto.PasswordChangeRequestDTO;
import com.hoperefund.member.service.MemberProfileService;
import com.hoperefund.member.service.MemberWithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원정보 · 자격 정보 조회 · 수정 · 비밀번호 변경 (F-01-04) · 회원 탈퇴 (F-01-05). 로그인한 회원만 (SecurityConfig 의 /api/**).
 * 회원번호는 주소에 넣지 않고 로그인 토큰에서 꺼낸다 (me = 로그인한 본인).
 * TODO: 팀 API 명세에 없는 주소 (제안) → 명세 추가 필요
 */
@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberProfileController {

    private final MemberProfileService memberProfileService;
    private final MemberWithdrawalService memberWithdrawalService;

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

    // POST /api/members/me/withdrawal — 회원 탈퇴 (F-01-05). 비밀번호를 다시 확인하고 지금 토큰도 무효로 한다
    // DELETE 가 아닌 이유: 행을 지우지 않고 상태만 바꾸며(30일 유예), 비밀번호를 본문으로 받아야 해서
    @PostMapping("/withdrawal")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody MemberWithdrawalRequestDTO request,
            Authentication authentication) {
        // details 에는 JWT 필터가 넣어 둔 토큰 정보(토큰 번호 · 만료 시각)가 있다 (로그아웃과 같음)
        TokenInfo tokenInfo = authentication.getDetails() instanceof TokenInfo info ? info : null;
        memberWithdrawalService.withdraw(memberId, request, tokenInfo);
        return ApiResponse.ok(null); // 돌려줄 데이터가 없으면 data 는 null (api-rules 3번)
    }
}
