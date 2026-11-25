package com.hoperefund.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.mapper.AccessLogMapper;
import com.hoperefund.common.service.TokenService;
import com.hoperefund.member.dto.LoginMemberDTO;
import com.hoperefund.member.dto.LoginRequestDTO;
import com.hoperefund.member.dto.LoginResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로그인 Service 테스트 (F-01-02)
 * DB 없이 Mapper를 대신해 검사 순서 · 실패 횟수 · 잠금 · 탈퇴 · ACCESS_LOG 기록 · 토큰 발급을 확인한다.
 */
class LoginServiceTest {

    private static final String IP = "127.0.0.1";
    private static final String PASSWORD = "password123";
    private static final String WRONG_PASSWORD = "wrong-password";
    private static final String JWT_SECRET = "test-jwt-secret-must-be-at-least-32-bytes";

    private MemberMapper memberMapper;
    private AccessLogMapper accessLogMapper;
    private TokenService tokenService;
    private PasswordEncoder passwordEncoder;
    private String passwordHash;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        accessLogMapper = mock(AccessLogMapper.class);
        passwordEncoder = new BCryptPasswordEncoder(4); // 테스트 속도를 위해 낮은 강도
        passwordHash = passwordEncoder.encode(PASSWORD);
        tokenService = new TokenService(JWT_SECRET, Duration.ofHours(2));
    }

    private LoginService service(boolean lockEnabled) {
        return new LoginService(
                memberMapper, accessLogMapper, passwordEncoder, tokenService, lockEnabled);
    }

    // 로그인 요청 → 성공 응답
    private LoginResponseDTO login(boolean lockEnabled, String loginId, String password) {
        return service(lockEnabled).login(new LoginRequestDTO(loginId, password), IP);
    }

    // 로그인 요청 → 난 오류를 꺼낸다 (BusinessException 이 안 나면 테스트 실패)
    private BusinessException loginError(boolean lockEnabled, String loginId, String password) {
        Throwable thrown = catchThrowable(() -> login(lockEnabled, loginId, password));
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return (BusinessException) thrown;
    }

    private LoginMemberDTO member(String status, String lockedYn, int failCount) {
        LoginMemberDTO member = new LoginMemberDTO();
        member.setMemberId(3L);
        member.setLoginId("hope_user");
        member.setPassword(passwordHash);
        member.setRole("USER");
        member.setStatus(status);
        member.setLoginFailCount(failCount);
        member.setLockedYn(lockedYn);
        return member;
    }

    // 성공: 토큰 발급 (토큰 안의 회원번호 · 역할 확인) · SUCCESS 기록 · 실패 횟수 초기화
    @Test
    void loginSuccessIssuesToken() {

        given(memberMapper.selectLoginMember("hope_user")).willReturn(member("ACTIVE", "N", 2));

        // 아이디는 앞뒤 공백 · 대문자를 정리해서 찾는다
        LoginResponseDTO response = login(true, " Hope_User ", PASSWORD);

        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getLoginId()).isEqualTo("hope_user");
        assertThat(response.getRole()).isEqualTo("USER");
        assertThat(response.getExpiresAt()).isNotNull();
        TokenService.TokenInfo info = tokenService.verify(response.getAccessToken()).orElseThrow();
        assertThat(info.memberId()).isEqualTo(3L);
        assertThat(info.role()).isEqualTo("USER");

        verify(memberMapper).resetLoginFail(3L);
        verify(accessLogMapper).insertAccessLog(3L, "/api/auth/login", IP, "SUCCESS");
    }

    // 실패한 적이 없으면 초기화 UPDATE를 하지 않는다
    @Test
    void loginSuccessWithoutFailuresSkipsReset() {

        given(memberMapper.selectLoginMember("hope_user")).willReturn(member("ACTIVE", "N", 0));

        login(true, "hope_user", PASSWORD);

        verify(memberMapper, never()).resetLoginFail(anyLong());
    }

    // 없는 아이디 → LOGIN_FAILED · 회원번호 없이 FAIL 기록
    @Test
    void unknownLoginIdFails() {

        given(memberMapper.selectLoginMember("nobody")).willReturn(null);

        BusinessException e = loginError(true, "nobody", PASSWORD);
        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(e.getCode()).isEqualTo("LOGIN_FAILED");

        verify(accessLogMapper)
                .insertAccessLog(isNull(), eq("/api/auth/login"), eq(IP), eq("FAIL"));
    }

    // 비밀번호 틀림 → LOGIN_FAILED (없는 아이디와 같은 응답) · 실패 횟수 + 1 (5번째면 10분 잠금) · FAIL 기록
    @Test
    void wrongPasswordIncreasesFailCount() {

        given(memberMapper.selectLoginMember("hope_user")).willReturn(member("ACTIVE", "N", 0));

        BusinessException e = loginError(true, "hope_user", WRONG_PASSWORD);
        assertThat(e.getCode()).isEqualTo("LOGIN_FAILED");
        assertThat(e.getMessage()).isEqualTo("아이디 또는 비밀번호가 맞지 않아요");

        verify(memberMapper).increaseLoginFail(3L, 5, 10);
        verify(accessLogMapper).insertAccessLog(3L, "/api/auth/login", IP, "FAIL");
    }

    // 잠금 중 → 비밀번호가 맞아도 LOGIN_LOCKED (비밀번호 비교 전에 막음) · 실패 횟수는 늘리지 않음
    @Test
    void lockedMemberIsRejectedBeforePasswordCheck() {

        given(memberMapper.selectLoginMember("hope_user")).willReturn(member("ACTIVE", "Y", 0));

        BusinessException e = loginError(true, "hope_user", PASSWORD);
        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(e.getCode()).isEqualTo("LOGIN_LOCKED");
        assertThat(e.getMessage()).isEqualTo("10분 뒤 다시 시도해 주세요");

        verify(memberMapper, never())
                .increaseLoginFail(anyLong(), any(Integer.class), any(Integer.class));
        verify(accessLogMapper).insertAccessLog(3L, "/api/auth/login", IP, "FAIL");
    }

    // 잠금을 끄면: 잠금 표시가 있어도 로그인되고, 틀려도 실패 횟수를 바꾸지 않는다
    @Test
    void lockDisabledIgnoresLockAndFailCount() {

        given(memberMapper.selectLoginMember("hope_user")).willReturn(member("ACTIVE", "Y", 0));

        LoginResponseDTO response = login(false, "hope_user", PASSWORD);
        assertThat(response.getAccessToken()).isNotBlank();

        loginError(false, "hope_user", WRONG_PASSWORD);
        verify(memberMapper, never())
                .increaseLoginFail(anyLong(), any(Integer.class), any(Integer.class));
    }

    // 탈퇴 회원 → 비밀번호가 맞을 때만 WITHDRAWN_MEMBER
    @Test
    void withdrawnMemberWithCorrectPassword() {

        given(memberMapper.selectLoginMember("hope_user")).willReturn(member("WITHDRAWN", "N", 0));

        BusinessException e = loginError(true, "hope_user", PASSWORD);
        assertThat(e.getCode()).isEqualTo("WITHDRAWN_MEMBER");
        assertThat(e.getMessage()).isEqualTo("탈퇴한 계정이에요");
        verify(accessLogMapper).insertAccessLog(3L, "/api/auth/login", IP, "FAIL");
    }

    // 탈퇴 회원 + 틀린 비밀번호 → LOGIN_FAILED (탈퇴 여부를 알려 주지 않음)
    @Test
    void withdrawnMemberWithWrongPasswordLooksLikeLoginFailed() {

        given(memberMapper.selectLoginMember("hope_user")).willReturn(member("WITHDRAWN", "N", 0));

        BusinessException e = loginError(true, "hope_user", WRONG_PASSWORD);
        assertThat(e.getCode()).isEqualTo("LOGIN_FAILED");
    }

    // BCrypt 한도(72바이트)를 넘는 비밀번호 → 서버 오류가 아니라 LOGIN_FAILED
    @Test
    void tooLongPasswordIsLoginFailed() {

        given(memberMapper.selectLoginMember("hope_user")).willReturn(member("ACTIVE", "N", 0));

        BusinessException e = loginError(true, "hope_user", "a".repeat(80));
        assertThat(e.getCode()).isEqualTo("LOGIN_FAILED");
    }

    // ACCESS_LOG 번호가 겹치면 한 번 더 시도하고, 그래도 안 되면 로그인은 그대로 성공
    @Test
    void accessLogDuplicateKeyIsRetriedOnceAndNeverBlocksLogin() {

        given(memberMapper.selectLoginMember("hope_user")).willReturn(member("ACTIVE", "N", 0));
        given(accessLogMapper.insertAccessLog(any(), any(), any(), any()))
                .willThrow(new DuplicateKeyException("겹침"));

        LoginResponseDTO response = login(true, "hope_user", PASSWORD);

        assertThat(response.getAccessToken()).isNotBlank();
        verify(accessLogMapper, times(2)).insertAccessLog(3L, "/api/auth/login", IP, "SUCCESS");
    }

    // ACCESS_LOG 저장이 다른 이유로 실패하면 다시 시도하지 않고, 로그인 실패 응답도 그대로 나간다
    @Test
    void accessLogFailureDoesNotChangeLoginResult() {

        given(memberMapper.selectLoginMember("nobody")).willReturn(null);
        given(accessLogMapper.insertAccessLog(any(), any(), any(), any()))
                .willThrow(new DataIntegrityViolationException("저장 실패"));

        BusinessException e = loginError(true, "nobody", PASSWORD);
        assertThat(e.getCode()).isEqualTo("LOGIN_FAILED");
        verify(accessLogMapper, times(1)).insertAccessLog(any(), any(), any(), any());
    }

    // 로그아웃: 그 토큰만 무효가 된다
    @Test
    void logoutRevokesToken() {

        String token = tokenService.issue(3L, "hope_user", "USER").token();

        service(true).logout(tokenService.verify(token).orElseThrow());

        assertThat(tokenService.verify(token)).isEmpty();
    }
}
