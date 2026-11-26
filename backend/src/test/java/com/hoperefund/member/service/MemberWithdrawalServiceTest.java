package com.hoperefund.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.service.TokenService;
import com.hoperefund.common.service.TokenService.TokenInfo;
import com.hoperefund.member.dto.MemberWithdrawalRequestDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원 탈퇴 Service 테스트 (F-01-05)
 * 비밀번호 재확인(맞음 · 틀림 · 72바이트 초과) · 상태 변경 · 로그아웃 · 없거나 이미 탈퇴한 회원을 확인한다.
 */
class MemberWithdrawalServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final String PASSWORD = "password1!";
    private static final TokenInfo TOKEN =
            new TokenInfo(MEMBER_ID, "test1234", "USER", "token-1", Instant.now().plusSeconds(60));

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberMapper memberMapper;
    private TokenService tokenService;
    private MemberWithdrawalService service;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        tokenService = mock(TokenService.class);
        service = new MemberWithdrawalService(memberMapper, passwordEncoder, tokenService);
        given(memberMapper.selectPassword(MEMBER_ID)).willReturn(passwordEncoder.encode(PASSWORD));
        given(memberMapper.withdrawMember(MEMBER_ID)).willReturn(1);
    }

    private static MemberWithdrawalRequestDTO request(String password) {
        return new MemberWithdrawalRequestDTO(password);
    }

    // 오류(e)가 그 상태 · 코드 · 문구의 BusinessException 인지 확인 (예외가 안 났으면 e 는 null → 실패)
    private static void assertError(Throwable e, HttpStatus status, String code, String message) {
        assertThat(e).isInstanceOf(BusinessException.class);
        BusinessException be = (BusinessException) e;
        assertThat(be.getStatus()).isEqualTo(status);
        assertThat(be.getCode()).isEqualTo(code);
        assertThat(be.getMessage()).isEqualTo(message);
    }

    // 비밀번호 맞음 → 탈퇴 처리 · 지금 토큰 로그아웃
    @Test
    void withdrawUpdatesStatusAndLogsOut() {
        service.withdraw(MEMBER_ID, request(PASSWORD), TOKEN);

        verify(memberMapper).withdrawMember(MEMBER_ID);
        verify(tokenService).logout(TOKEN);
    }

    // 비밀번호 틀림 → 400 CURRENT_PASSWORD_MISMATCH · 탈퇴 · 로그아웃 안 함
    @Test
    void withdrawWrongPassword() {
        Throwable e = catchThrowable(() -> service.withdraw(MEMBER_ID, request("wrong1!"), TOKEN));

        String code = "CURRENT_PASSWORD_MISMATCH";
        assertError(e, HttpStatus.BAD_REQUEST, code, "비밀번호가 맞지 않아요");
        verify(memberMapper, never()).withdrawMember(anyLong());
        verify(tokenService, never()).logout(any());
    }

    // 72바이트가 넘는 비밀번호 → BCrypt 오류 대신 "틀린 비밀번호"
    @Test
    void withdrawTooLongPasswordIsMismatch() {
        String longPassword = "a".repeat(100);

        Throwable e =
                catchThrowable(() -> service.withdraw(MEMBER_ID, request(longPassword), TOKEN));

        String code = "CURRENT_PASSWORD_MISMATCH";
        assertError(e, HttpStatus.BAD_REQUEST, code, "비밀번호가 맞지 않아요");
        verify(memberMapper, never()).withdrawMember(anyLong());
    }

    // 없거나 이미 탈퇴한 회원 (비밀번호 조회가 ACTIVE 만) → 404
    @Test
    void withdrawNotFound() {
        given(memberMapper.selectPassword(MEMBER_ID)).willReturn(null);

        Throwable e = catchThrowable(() -> service.withdraw(MEMBER_ID, request(PASSWORD), TOKEN));

        assertError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "회원 정보를 찾을 수 없어요");
        verify(memberMapper, never()).withdrawMember(anyLong());
    }

    // 비밀번호 확인 뒤 그사이 다른 요청으로 이미 탈퇴됨 (0행) → 404 · 로그아웃 안 함
    @Test
    void withdrawAlreadyWithdrawnConcurrently() {
        given(memberMapper.withdrawMember(MEMBER_ID)).willReturn(0);

        Throwable e = catchThrowable(() -> service.withdraw(MEMBER_ID, request(PASSWORD), TOKEN));

        assertError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "회원 정보를 찾을 수 없어요");
        verify(tokenService, never()).logout(any());
    }

    // 토큰 정보가 없어도(테스트 · 다른 인증 방식) 탈퇴는 된다
    @Test
    void withdrawWithoutTokenInfo() {
        service.withdraw(MEMBER_ID, request(PASSWORD), null);

        verify(memberMapper).withdrawMember(MEMBER_ID);
        verify(tokenService, never()).logout(any());
    }
}
