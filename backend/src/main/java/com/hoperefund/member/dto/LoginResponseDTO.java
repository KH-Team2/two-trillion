package com.hoperefund.member.dto;

import java.time.LocalDateTime;

/**
 * 로그인 성공 응답 DTO (Spring Boot → React · F-01-02)
 * 화면은 accessToken을 보관했다가 요청 헤더 Authorization: Bearer <accessToken>으로 보낸다.
 * loginId · role은 화면의 "누가 로그인했나" 표시(관리자 메뉴 등)에 쓴다. 회원번호는 돌려주지 않는다 (api-rules 2번).
 */
public record LoginResponseDTO(
        String accessToken,
        String tokenType, // 항상 "Bearer"
        LocalDateTime expiresAt, // 토큰 만료 시각 (한국 시간)
        String loginId,
        String role) {}
