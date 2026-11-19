package com.hoperefund.member.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 이메일 인증번호 발송 응답. */
@Getter
@AllArgsConstructor
public class EmailVerificationResponseDTO {

    // 인증 확인 · 회원가입 요청 때 다시 보내는 번호표 (추측할 수 없는 무작위 값)
    private final String verificationId;

    private final Instant expiresAt; // 인증번호 만료 시각

    // 개발 설정(EMAIL_DEV_CODE_ENABLED=true)일 때만 인증번호를 담고, 그 외에는 null
    // TODO: 메일 발송을 구현하면 제거
    private final String devCode;
}
