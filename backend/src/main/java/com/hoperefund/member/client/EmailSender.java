package com.hoperefund.member.client;

/**
 * 이메일 인증번호를 메일로 보내는 창구 (인터페이스) · prediction/client/CalcClient 와 같은 구조
 *
 * <p>구현은 2개예요. application.yml 의 member.mail.mode(환경변수 MAIL_MODE)로 골라요.
 *
 * <ul>
 *   <li>log: LogEmailSender (메일을 보내지 않고 로그만 · SMTP 계정 없이 개발할 때 · 기본값)
 *   <li>smtp: SmtpEmailSender (메일 서버(SMTP)로 실제 발송)
 * </ul>
 *
 * <p>EmailVerificationService 는 어느 쪽인지 몰라도 돼요 (스프링이 고른 구현을 주입 = DI).
 */
public interface EmailSender {

    /**
     * 인증번호 메일을 보낸다. 보내지 못하면 BusinessException(503 · MAIL_UNAVAILABLE)을 던진다.
     *
     * @param to 받는 이메일 (소문자로 정리된 값)
     * @param code 6자리 인증번호
     */
    void sendVerificationCode(String to, String code);

    /**
     * 비밀번호 재설정 인증 코드 메일을 보낸다 (F-01-03). 보내지 못하면 BusinessException(503 · MAIL_UNAVAILABLE)을 던진다.
     *
     * @param to 받는 이메일 (소문자로 정리된 값)
     * @param code 6자리 인증 코드
     */
    void sendPasswordResetCode(String to, String code);
}
