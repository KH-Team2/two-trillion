package com.hoperefund.member.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.service.TokenService.TokenInfo;
import com.hoperefund.member.dto.LoginRequestDTO;
import com.hoperefund.member.dto.LoginResponseDTO;
import com.hoperefund.member.dto.MemberAvailabilityResponseDTO;
import com.hoperefund.member.dto.MemberSignupRequestDTO;
import com.hoperefund.member.dto.MemberSignupResponseDTO;
import com.hoperefund.member.service.LoginService;
import com.hoperefund.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 API Controller (F-01-01 회원가입 · F-01-02 로그인 · 로그아웃)
 * 요청 수신 · 형식 검증(@Valid) · 응답만 담당하고 처리는 Service에 맡긴다.
 * 주소: POST /api/auth/members · GET /api/auth/members/availability · POST /api/auth/login · POST /api/auth/logout
 * 응답: 성공은 모두 200 + ApiResponse 봉투 (docs/api-rules.md · 11/09 팀 합의)
 */
@RestController
@RequestMapping("/api/auth")
public class MemberController {

    private final MemberService memberService;
    private final LoginService loginService;

    public MemberController(MemberService memberService, LoginService loginService) {
        this.memberService = memberService;
        this.loginService = loginService;
    }

    // 회원가입: 성공 시 200 {success, data: {memberId, loginId, email, name, role, createdAt}}
    @PostMapping("/members")
    public ApiResponse<MemberSignupResponseDTO> signup(
            @Valid @RequestBody MemberSignupRequestDTO request) {

        return ApiResponse.ok(memberService.signup(request));
    }

    // 아이디 · 이메일 중복확인: ?loginId= 또는 ?email= 중 하나만 보낸다
    // TODO: 팀 API 명세에 없는 주소 (제안) → 명세 추가 필요
    @GetMapping("/members/availability")
    public ApiResponse<MemberAvailabilityResponseDTO> checkAvailability(
            @RequestParam(required = false) String loginId,
            @RequestParam(required = false) String email) {

        if ((loginId == null) == (email == null)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VALIDATION", "아이디 또는 이메일 중 하나를 입력해 주세요");
        }

        MemberAvailabilityResponseDTO response =
                loginId != null
                        ? memberService.checkLoginId(loginId)
                        : memberService.checkEmail(email);

        return ApiResponse.ok(response);
    }

    // 로그인: 성공 시 200 {success, data: {accessToken, tokenType, expiresAt, loginId, role}}
    // 접속 IP는 ACCESS_LOG 기록용 (X-Forwarded-For는 화면이 바꿀 수 있어 쓰지 않음)
    @PostMapping("/login")
    public ApiResponse<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {

        return ApiResponse.ok(loginService.login(request, httpRequest.getRemoteAddr()));
    }

    // 로그아웃: 로그인 필요 (SecurityConfig) · 성공 시 200 + data: null
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {

        loginService.logout((TokenInfo) authentication.getDetails());
        return ApiResponse.ok(null);
    }
}
