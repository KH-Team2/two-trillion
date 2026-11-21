package com.hoperefund.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.client.EmailSender;
import com.hoperefund.member.dto.EmailVerificationResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 이메일 인증 Service 테스트
 * 인증번호 발급 · 확인 · 오류 · 만료 · 실패 횟수 · 가입 전 인증 확인을 확인한다.
 */
class EmailVerificationServiceTest {

    private static final String EMAIL = "hope@example.com";

    private MemberMapper memberMapper;
    private EmailSender emailSender;
    private MutableClock clock;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        emailSender = mock(EmailSender.class);
        clock = new MutableClock(Instant.parse("2026-11-10T03:00:00Z"));
        service =
                new EmailVerificationService(
                        memberMapper, new BCryptPasswordEncoder(), emailSender, clock, true);
    }

    // 발급: 번호표 · 5분 뒤 만료 · (개발 설정) 6자리 인증번호
    @Test
    void sendIssuesCode() {

        EmailVerificationResponseDTO response = service.send(" Hope@Example.com ");

        assertThat(response.getVerificationId()).isNotBlank();
        assertThat(response.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
        assertThat(response.getDevCode()).matches("^[0-9]{6}$");
    }

    // 발급하면 소문자로 정리한 이메일로 같은 인증번호를 메일로 보냄
    @Test
    void sendMailsCodeToNormalizedEmail() {

        EmailVerificationResponseDTO response = service.send(" Hope@Example.com ");

        verify(emailSender).sendVerificationCode(EMAIL, response.getDevCode());
    }

    // 이미 가입된 이메일이면 메일을 보내지 않음
    @Test
    void sendSkipsMailForRegisteredEmail() {

        given(memberMapper.countByEmail(EMAIL)).willReturn(1);

        assertThatThrownBy(() -> service.send(EMAIL)).isInstanceOf(BusinessException.class);
        verify(emailSender, never()).sendVerificationCode(anyString(), anyString());
    }

    // 메일 발송 실패(503 MAIL_UNAVAILABLE)는 그대로 전달
    @Test
    void sendPropagatesMailFailure() {

        willThrow(
                        new BusinessException(
                                HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "메일을 보내지 못했어요"))
                .given(emailSender)
                .sendVerificationCode(eq(EMAIL), anyString());

        assertThatThrownBy(() -> service.send(EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("MAIL_UNAVAILABLE");
    }

    // 개발 설정이 꺼져 있으면 인증번호를 응답에 담지 않음
    @Test
    void sendHidesCodeWhenDevDisabled() {

        EmailVerificationService prod =
                new EmailVerificationService(
                        memberMapper, new BCryptPasswordEncoder(), emailSender, clock, false);

        assertThat(prod.send(EMAIL).getDevCode()).isNull();
    }

    // 이미 가입된 이메일은 발급하지 않음
    @Test
    void sendRejectsRegisteredEmail() {

        given(memberMapper.countByEmail(EMAIL)).willReturn(1);

        assertThatThrownBy(() -> service.send("HOPE@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_EMAIL");
    }

    // 정상 인증 후 가입 전 확인 통과 → 사용 처리 후에는 다시 쓸 수 없음
    @Test
    void confirmThenRequireVerified() {

        EmailVerificationResponseDTO sent = service.send(EMAIL);
        service.confirm(sent.getVerificationId(), sent.getDevCode());

        assertThatCode(() -> service.requireVerified(sent.getVerificationId(), EMAIL))
                .doesNotThrowAnyException();

        service.complete(sent.getVerificationId());
        assertThatThrownBy(() -> service.requireVerified(sent.getVerificationId(), EMAIL))
                .extracting("code")
                .isEqualTo("EMAIL_NOT_VERIFIED");
    }

    // 잘못된 인증 코드 → 실패 + 안내 문구
    @Test
    void wrongCodeFails() {

        EmailVerificationResponseDTO sent = service.send(EMAIL);
        String wrong = sent.getDevCode().equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> service.confirm(sent.getVerificationId(), wrong))
                .isInstanceOf(BusinessException.class)
                .hasMessage("인증 코드가 맞지 않거나 만료됐어요")
                .extracting("code")
                .isEqualTo("VERIFICATION_FAILED");
    }

    // 만료된 인증 코드 → 실패 (맞는 코드여도)
    @Test
    void expiredCodeFails() {

        EmailVerificationResponseDTO sent = service.send(EMAIL);
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.confirm(sent.getVerificationId(), sent.getDevCode()))
                .hasMessage("인증 코드가 맞지 않거나 만료됐어요")
                .extracting("code")
                .isEqualTo("VERIFICATION_EXPIRED");
    }

    // 5번 틀리면 번호표 폐기 → 맞는 코드도 실패
    @Test
    void fiveFailuresInvalidate() {

        EmailVerificationResponseDTO sent = service.send(EMAIL);
        String wrong = sent.getDevCode().equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.confirm(sent.getVerificationId(), wrong))
                    .extracting("code")
                    .isEqualTo("VERIFICATION_FAILED");
        }
        assertThatThrownBy(() -> service.confirm(sent.getVerificationId(), wrong))
                .extracting("code")
                .isEqualTo("VERIFICATION_LIMIT");
        assertThatThrownBy(() -> service.confirm(sent.getVerificationId(), sent.getDevCode()))
                .extracting("code")
                .isEqualTo("VERIFICATION_FAILED");
    }

    // 인증하지 않았거나 · 다른 이메일 · 인증 후 30분 지남 → 가입 불가
    @Test
    void requireVerifiedRejectsInvalidCases() {

        EmailVerificationResponseDTO sent = service.send(EMAIL);

        assertThatThrownBy(() -> service.requireVerified(sent.getVerificationId(), EMAIL))
                .extracting("code")
                .isEqualTo("EMAIL_NOT_VERIFIED");

        service.confirm(sent.getVerificationId(), sent.getDevCode());
        assertThatThrownBy(
                        () ->
                                service.requireVerified(
                                        sent.getVerificationId(), "other@example.com"))
                .extracting("code")
                .isEqualTo("EMAIL_NOT_VERIFIED");

        clock.advance(Duration.ofMinutes(30));
        assertThatThrownBy(() -> service.requireVerified(sent.getVerificationId(), EMAIL))
                .extracting("code")
                .isEqualTo("EMAIL_NOT_VERIFIED");

        assertThatThrownBy(() -> service.requireVerified("unknown", EMAIL))
                .extracting("code")
                .isEqualTo("EMAIL_NOT_VERIFIED");
    }

    // 테스트용 시계: 시간을 앞으로 돌릴 수 있다
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
