package com.hoperefund.member.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 메일을 실제로 보내지 않는 발송기 (SMTP 계정 없이 개발할 때 · MAIL_MODE=log 또는 비어 있을 때)
 *
 * <p>인증번호는 메일로 가지 않으므로, 개발할 때는 EMAIL_DEV_CODE_ENABLED=true 로 켜서 발송 API 응답(devCode)으로 확인한다.
 * 운영에서 MAIL_MODE=smtp 를 빠뜨려도 알 수 있게 서버가 켜질 때 경고 로그를 남긴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "member.mail.mode", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    public LogEmailSender() {
        log.warn("MAIL_MODE=log: 인증번호 메일을 보내지 않아요. 실제 서비스에서는 MAIL_MODE=smtp 와 메일 계정을 설정하세요");
    }

    @Override
    public void sendVerificationCode(String to, String code) {
        // 인증번호 · 이메일 주소는 로그에 남기지 않는다
        log.info("인증번호 메일 발송 생략 (MAIL_MODE=log)");
    }

    @Override
    public void sendPasswordResetCode(String to, String code) {
        // 인증 코드 · 이메일 주소는 로그에 남기지 않는다
        log.info("비밀번호 재설정 메일 발송 생략 (MAIL_MODE=log)");
    }
}
