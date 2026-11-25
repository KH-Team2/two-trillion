package com.hoperefund.member.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 비밀번호 재설정 인증 코드 발송 응답 (F-01-03). */
@Getter
@AllArgsConstructor
public class PasswordResetResponseDTO {

    // 인증 코드 만료 시각 (화면의 남은 시간 표시용 · 실제 만료 판단은 DB 시각 EXPIRES_AT 으로 한다)
    private final Instant expiresAt;

    // 개발 설정(EMAIL_DEV_CODE_ENABLED=true)일 때만 인증 코드를 담고, 그 외에는 null
    private final String devCode;
}
