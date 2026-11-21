package com.hoperefund.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.client.EmailSender;
import com.hoperefund.member.dto.PasswordResetCompleteRequestDTO;
import com.hoperefund.member.dto.PasswordResetDTO;
import com.hoperefund.member.dto.PasswordResetMemberDTO;
import com.hoperefund.member.dto.PasswordResetResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import com.hoperefund.member.mapper.PasswordResetMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 아이디 찾기 · 비밀번호 재설정 Service 테스트 (F-01-03)
 * 대상 회원 확인(없음 · 탈퇴 · 미인증) · 마스킹 · 코드 발급 · 코드 확인(만료 · 사용 · 틀림 · 5번) · 비밀번호 저장을 확인한다.
 */
class PasswordResetServiceTest {

    private static final String EMAIL = "hope@example.com";
    private static final long MEMBER_ID = 7L;
    private static final long RESET_ID = 30L;
    private static final String CODE = "123456";
    private static final String NEW_PASSWORD = "newPassword1!";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);

    private MemberMapper memberMapper;
    private PasswordResetMapper passwordResetMapper;
    private EmailSender emailSender;
    private PasswordResetService service; // 이메일 인증을 켠 상태 · 개발용 코드 표시 켬

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        passwordResetMapper = mock(PasswordResetMapper.class);
        emailSender = mock(EmailSender.class);
        service = newService(true);
        given(memberMapper.selectResetMember(EMAIL)).willReturn(member("ACTIVE", "Y"));
    }

    private PasswordResetService newService(boolean verificationRequired) {
        return new PasswordResetService(
                memberMapper,
                passwordResetMapper,
                passwordEncoder,
                emailSender,
                clock,
                verificationRequired,
                true);
    }

    private static PasswordResetMemberDTO member(String status, String emailVerifiedYn) {
        PasswordResetMemberDTO m = new PasswordResetMemberDTO();
        m.setMemberId(MEMBER_ID);
        m.setLoginId("test1234");
        m.setStatus(status);
        m.setEmailVerifiedYn(emailVerifiedYn);
        return m;
    }

    // 저장된 재설정 1행을 준비한다 (코드 123456)
    private void givenReset(String usedYn, int failCount, String expiredYn) {
        PasswordResetDTO reset = new PasswordResetDTO();
        reset.setResetId(RESET_ID);
        reset.setMemberId(MEMBER_ID);
        reset.setCodeHash(passwordEncoder.encode(CODE));
        reset.setUsedYn(usedYn);
        reset.setFailCount(failCount);
        reset.setExpiredYn(expiredYn);
        given(passwordResetMapper.selectLatestResetId(MEMBER_ID)).willReturn(RESET_ID);
        given(passwordResetMapper.selectResetForUpdate(RESET_ID)).willReturn(reset);
    }

    private static PasswordResetCompleteRequestDTO request(String code) {
        return new PasswordResetCompleteRequestDTO(EMAIL, code, NEW_PASSWORD, NEW_PASSWORD);
    }

    private static void assertError(Throwable e, HttpStatus status, String code, String message) {
        BusinessException be = (BusinessException) e;
        assertThat(be.getStatus()).isEqualTo(status);
        assertThat(be.getCode()).isEqualTo(code);
        assertThat(be.getMessage()).isEqualTo(message);
    }

    // ---------- 아이디 찾기 ----------

    // 아이디 앞 2글자만 보이고 나머지는 * · 이메일은 소문자로 바꿔 찾음
    @Test
    void findLoginIdReturnsMaskedId() {

        assertThat(service.findLoginId(" Hope@Example.com ").getMaskedLoginId())
                .isEqualTo("te******");
    }

    @Test
    void maskKeepsLength() {

        assertThat(PasswordResetService.maskLoginId("abcd")).isEqualTo("ab**");
        assertThat(PasswordResetService.maskLoginId("hope_refund_user"))
                .isEqualTo("ho**************");
    }

    // 가입되지 않은 이메일 → 404 「가입 정보를 찾을 수 없어요」
    @Test
    void unknownEmailReturns404() {

        assertThatThrownBy(() -> service.findLoginId("nobody@example.com"))
                .satisfies(
                        e -> assertError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "가입 정보를 찾을 수 없어요"));
    }

    // 탈퇴 회원 → 없는 회원과 같은 404
    @Test
    void withdrawnMemberReturns404() {

        given(memberMapper.selectResetMember(EMAIL)).willReturn(member("WITHDRAWN", "Y"));

        assertThatThrownBy(() -> service.findLoginId(EMAIL))
                .satisfies(
                        e -> assertError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "가입 정보를 찾을 수 없어요"));
    }

    // 이메일 인증을 켰을 때: 인증하지 않은 회원(N) → 404 · 메일도 보내지 않음
    @Test
    void unverifiedMemberReturns404WhenVerificationRequired() {

        given(memberMapper.selectResetMember(EMAIL)).willReturn(member("ACTIVE", "N"));

        assertThatThrownBy(() -> service.findLoginId(EMAIL)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.sendCode(EMAIL)).isInstanceOf(BusinessException.class);
        verify(emailSender, never()).sendPasswordResetCode(anyString(), anyString());
    }

    // 이메일 인증을 껐을 때(개발 중): 인증하지 않은 회원(N)도 허용
    @Test
    void unverifiedMemberAllowedWhenVerificationOff() {

        given(memberMapper.selectResetMember(EMAIL)).willReturn(member("ACTIVE", "N"));

        assertThat(newService(false).findLoginId(EMAIL).getMaskedLoginId()).isEqualTo("te******");
    }

    // ---------- 코드 발송 ----------

    // 발송: 6자리 코드를 메일로 보내고 · 해시만 10분 유효로 저장 · 응답 만료 시각 10분 뒤
    @Test
    void sendCodeMailsAndStoresHash() {

        PasswordResetResponseDTO response = service.sendCode(" Hope@Example.com ");

        assertThat(response.getDevCode()).matches("^[0-9]{6}$");
        assertThat(response.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
        verify(emailSender).sendPasswordResetCode(EMAIL, response.getDevCode());

        ArgumentCaptor<PasswordResetDTO> captor = ArgumentCaptor.forClass(PasswordResetDTO.class);
        verify(passwordResetMapper).insertReset(captor.capture());
        PasswordResetDTO saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getValidMinutes()).isEqualTo(10);
        assertThat(saved.getCodeHash()).isNotEqualTo(response.getDevCode()); // 원문 저장 안 함
        assertThat(passwordEncoder.matches(response.getDevCode(), saved.getCodeHash())).isTrue();
    }

    // 개발용 코드 표시를 끄면 응답에 코드 없음
    @Test
    void sendCodeHidesCodeWhenDevCodeOff() {

        PasswordResetService prod =
                new PasswordResetService(
                        memberMapper,
                        passwordResetMapper,
                        passwordEncoder,
                        emailSender,
                        clock,
                        true,
                        false);

        assertThat(prod.sendCode(EMAIL).getDevCode()).isNull();
    }

    // 메일 발송 실패(503) → 코드를 저장하지 않음
    @Test
    void sendCodeSkipsSaveWhenMailFails() {

        willThrow(
                        new BusinessException(
                                HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "메일을 보내지 못했어요"))
                .given(emailSender)
                .sendPasswordResetCode(eq(EMAIL), anyString());

        assertThatThrownBy(() -> service.sendCode(EMAIL)).isInstanceOf(BusinessException.class);
        verify(passwordResetMapper, never()).insertReset(any());
    }

    // ---------- 코드 확인 · 새 비밀번호 저장 ----------

    // 정상: 사용 처리 · 새 비밀번호를 BCrypt 로 저장
    @Test
    void completeSavesNewPassword() {

        givenReset("N", 0, "N");

        service.complete(request(CODE));

        verify(passwordResetMapper).markUsed(RESET_ID);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(memberMapper).updatePassword(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue()).isNotEqualTo(NEW_PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, captor.getValue())).isTrue();
    }

    // 코드가 틀림 → 틀린 횟수 + 1 · 비밀번호 그대로
    @Test
    void wrongCodeIncreasesFailCount() {

        givenReset("N", 0, "N");

        assertThatThrownBy(() -> service.complete(request("000000")))
                .satisfies(
                        e ->
                                assertError(
                                        e,
                                        HttpStatus.BAD_REQUEST,
                                        "RESET_CODE_FAILED",
                                        "인증 코드가 맞지 않아요"));
        verify(passwordResetMapper).increaseFailCount(RESET_ID);
        verify(memberMapper, never()).updatePassword(anyLong(), anyString());
    }

    // 5번째로 틀림 → 「인증 코드를 다시 받아 주세요」
    @Test
    void fifthWrongCodeRequiresResend() {

        givenReset("N", 4, "N");

        assertThatThrownBy(() -> service.complete(request("000000")))
                .satisfies(
                        e ->
                                assertError(
                                        e,
                                        HttpStatus.BAD_REQUEST,
                                        "RESET_CODE_LIMIT",
                                        "인증 코드를 다시 받아 주세요"));
        verify(passwordResetMapper).increaseFailCount(RESET_ID);
    }

    // 이미 5번 틀린 코드 → 맞는 코드를 넣어도 무효
    @Test
    void codeAfterFiveFailsIsInvalid() {

        givenReset("N", 5, "N");

        assertThatThrownBy(() -> service.complete(request(CODE)))
                .satisfies(
                        e ->
                                assertError(
                                        e,
                                        HttpStatus.BAD_REQUEST,
                                        "RESET_CODE_EXPIRED",
                                        "인증 코드를 다시 받아 주세요"));
        verify(memberMapper, never()).updatePassword(anyLong(), anyString());
    }

    // 만료된 코드 → 「인증 코드를 다시 받아 주세요」
    @Test
    void expiredCodeRequiresResend() {

        givenReset("N", 0, "Y");

        assertThatThrownBy(() -> service.complete(request(CODE)))
                .satisfies(
                        e ->
                                assertError(
                                        e,
                                        HttpStatus.BAD_REQUEST,
                                        "RESET_CODE_EXPIRED",
                                        "인증 코드를 다시 받아 주세요"));
        verify(memberMapper, never()).updatePassword(anyLong(), anyString());
    }

    // 이미 쓴 코드 → 다시 쓸 수 없음
    @Test
    void usedCodeCannotBeReused() {

        givenReset("Y", 0, "N");

        assertThatThrownBy(() -> service.complete(request(CODE)))
                .satisfies(
                        e ->
                                assertError(
                                        e,
                                        HttpStatus.BAD_REQUEST,
                                        "RESET_CODE_EXPIRED",
                                        "인증 코드를 다시 받아 주세요"));
        verify(passwordResetMapper, never()).markUsed(anyLong());
    }

    // 코드를 받은 적 없음 → 「인증 코드를 다시 받아 주세요」
    @Test
    void noCodeRequiresResend() {

        assertThatThrownBy(() -> service.complete(request(CODE)))
                .satisfies(
                        e ->
                                assertError(
                                        e,
                                        HttpStatus.BAD_REQUEST,
                                        "RESET_CODE_EXPIRED",
                                        "인증 코드를 다시 받아 주세요"));
    }

    // 새 비밀번호 확인이 다름 → 400 · 코드 기회를 쓰지 않음
    @Test
    void passwordMismatchDoesNotUseCodeAttempt() {

        givenReset("N", 0, "N");

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        new PasswordResetCompleteRequestDTO(
                                                EMAIL, "000000", NEW_PASSWORD, "different1!")))
                .satisfies(
                        e ->
                                assertError(
                                        e,
                                        HttpStatus.BAD_REQUEST,
                                        "VALIDATION",
                                        "비밀번호 확인이 일치하지 않아요"));
        verify(passwordResetMapper, never()).increaseFailCount(anyLong());
    }

    // 가입되지 않은 이메일로 완료 요청 → 404
    @Test
    void completeUnknownEmailReturns404() {

        assertThatThrownBy(
                        () ->
                                service.complete(
                                        new PasswordResetCompleteRequestDTO(
                                                "nobody@example.com",
                                                CODE,
                                                NEW_PASSWORD,
                                                NEW_PASSWORD)))
                .satisfies(
                        e -> assertError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "가입 정보를 찾을 수 없어요"));
    }
}
