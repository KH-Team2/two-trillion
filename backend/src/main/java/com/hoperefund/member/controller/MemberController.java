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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입 · 중복확인 · 로그인 · 로그아웃 (F-01-01 · F-01-02).
 * 로그아웃을 뺀 주소는 로그인 전에도 부를 수 있다 (SecurityConfig 의 /api/auth/**).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final LoginService loginService;

    // POST /api/auth/members — 회원가입
    @PostMapping("/members")
    public ApiResponse<MemberSignupResponseDTO> signup(
            @Valid @RequestBody MemberSignupRequestDTO request) {
        return ApiResponse.ok(memberService.signup(request));
    }

    // GET /api/auth/members/availability?loginId= 또는 ?email= — 아이디 · 이메일 중복확인 (둘 중 하나만 보낸다)
    // TODO: 팀 API 명세에 없는 주소 (제안) → 명세 추가 필요
    @GetMapping("/members/availability")
    public ApiResponse<MemberAvailabilityResponseDTO> checkAvailability(
            @RequestParam(required = false) String loginId,
            @RequestParam(required = false) String email) {
        // 둘 다 없거나 둘 다 있으면 400
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

    // POST /api/auth/login — 로그인. 접속 IP 는 ACCESS_LOG 기록용
    @PostMapping("/login")
    public ApiResponse<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {
        // X-Forwarded-For 는 화면이 마음대로 바꿀 수 있어 쓰지 않는다
        return ApiResponse.ok(loginService.login(request, httpRequest.getRemoteAddr()));
    }

    // POST /api/auth/logout — 로그아웃. 지금 쓰는 토큰을 무효로 만든다 (로그인 필요)
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        // details 에는 JWT 필터가 넣어 둔 토큰 정보(토큰 번호 · 만료 시각)가 있다
        loginService.logout((TokenInfo) authentication.getDetails());
        return ApiResponse.ok(null); // 돌려줄 데이터가 없으면 data 는 null (api-rules 3번)
    }
}
