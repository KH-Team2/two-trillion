package com.hoperefund.member.client;

import com.hoperefund.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 메일 서버(SMTP)로 인증번호 메일을 실제로 보낸다 (MAIL_MODE=smtp 일 때만 Bean 등록)
 *
 * <p>메일 서버 주소 · 계정은 application.yml 의 spring.mail (환경변수 MAIL_HOST · MAIL_PORT · MAIL_USERNAME · MAIL_PASSWORD).
 * JavaMailSender 는 spring-boot-starter-mail 이 spring.mail 설정으로 만들어 준다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "member.mail.mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    static final String SUBJECT = "[희망환급] 이메일 인증번호";
    static final String RESET_SUBJECT = "[희망환급] 비밀번호 재설정 인증 코드";

    private final JavaMailSender mailSender;
    private final String from; // 보내는 사람 주소

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${member.mail.from:}") String from) {
        // 보내는 주소가 없으면 메일 서버가 거절하므로, 서버를 켤 때 바로 알린다 (HttpCalcClient 의 토큰 검사와 같은 방식)
        if (from == null || from.isBlank()) {
            throw new IllegalStateException(
                    "MAIL_MODE=smtp 인데 보내는 주소(MAIL_FROM 또는 MAIL_USERNAME)가 비어 있어요.");
        }
        this.mailSender = mailSender;
        this.from = from.trim();
    }

    @Override
    public void sendVerificationCode(String to, String code) {
        send(
                to,
                SUBJECT,
                "희망환급 회원가입 인증번호는 ["
                        + code
                        + "] 입니다.\n"
                        + "5분 안에 회원가입 화면에 입력해 주세요.\n\n"
                        + "본인이 요청하지 않았다면 이 메일은 무시해 주세요.");
    }

    @Override
    public void sendPasswordResetCode(String to, String code) {
        send(
                to,
                RESET_SUBJECT,
                "희망환급 비밀번호 재설정 인증 코드는 ["
                        + code
                        + "] 입니다.\n"
                        + "10분 안에 비밀번호 재설정 화면에 입력해 주세요.\n\n"
                        + "본인이 요청하지 않았다면 이 메일은 무시해 주세요. 비밀번호는 바뀌지 않아요.");
    }

    // 메일 1통 발송 (가입 인증 · 비밀번호 재설정 공통)
    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 원인(계정 오류 · 연결 실패 등)은 서버 로그에만 · 화면에는 짧은 안내만 (인증번호는 로그에 남기지 않는다)
            log.error("메일 발송 실패 ({}): {}", subject, e.getClass().getSimpleName(), e);
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_UNAVAILABLE",
                    "메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요");
        }
    }
}
