package com.hoperefund.member.dto;

/**
 * 회원가입 성공 응답 DTO (Spring Boot → React)
 * 비밀번호 등 민감정보는 돌려주지 않는다.
 */
public record MemberSignupResponseDTO(Long memberId, String loginId, String message) {}
