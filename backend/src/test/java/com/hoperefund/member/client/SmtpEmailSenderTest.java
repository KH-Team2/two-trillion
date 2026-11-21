package com.hoperefund.member.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP 메일 발송기 테스트 (실제 메일 서버는 부르지 않고 JavaMailSender 를 가짜로 바꿔 확인)
 * 메일 내용 · 발송 실패 → 503 MAIL_UNAVAILABLE · 보내는 주소 누락 시 서버 시작 실패를 확인한다.
 */
class SmtpEmailSenderTest {

    private JavaMailSender mailSender;
    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        sender = new SmtpEmailSender(mailSender, " noreply@example.com ");
    }

    // 정상: 받는 주소 · 보내는 주소 · 제목 · 본문의 인증번호
    @Test
    void sendsVerificationMail() {

        sender.sendVerificationCode("hope@example.com", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("hope@example.com");
        assertThat(message.getFrom()).isEqualTo("noreply@example.com");
        assertThat(message.getSubject()).isEqualTo(SmtpEmailSender.SUBJECT);
        assertThat(message.getText()).contains("123456").contains("5분");
    }

    // 메일 서버 오류 → 503 MAIL_UNAVAILABLE
    @Test
    void mailFailureBecomesUnavailable() {

        willThrow(new MailSendException("연결 실패"))
                .given(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.sendVerificationCode("hope@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        e -> {
                            BusinessException be = (BusinessException) e;
                            assertThat(be.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                            assertThat(be.getCode()).isEqualTo("MAIL_UNAVAILABLE");
                        });
    }

    // 보내는 주소가 비어 있으면 서버를 켤 때 바로 실패
    @Test
    void blankFromFailsAtStartup() {

        assertThatThrownBy(() -> new SmtpEmailSender(mailSender, " "))
                .isInstanceOf(IllegalStateException.class);
    }
}
