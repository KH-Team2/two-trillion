package com.hoperefund.member.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인 성공 응답 (F-01-02).
 * 화면은 accessToken 을 보관했다가 요청 헤더 Authorization: Bearer <accessToken> 으로 보낸다.
 * 회원번호는 돌려주지 않는다 (api-rules 2번 · 서버가 토큰에서 꺼낸다).
 */
@Getter
@AllArgsConstructor
public class LoginResponseDTO {

    private final String accessToken;
    private final String tokenType; // 항상 "Bearer"
    private final LocalDateTime expiresAt; // 토큰 만료 시각 (한국 시간)
    private final String loginId; // 화면의 "누가 로그인했나" 표시용
    private final String role; // USER · ADMIN (관리자 메뉴 표시용)
}
