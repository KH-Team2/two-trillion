package com.hoperefund.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 DTO (React → Spring Boot · F-01-02)
 * 비밀번호는 로그 · 응답에 남기지 않는다.
 */
public record LoginRequestDTO(
        @NotBlank(message = "아이디를 입력해 주세요") @Size(max = 50, message = "아이디 또는 비밀번호가 맞지 않아요")
                String loginId,
        @NotBlank(message = "비밀번호를 입력해 주세요") @Size(max = 100, message = "아이디 또는 비밀번호가 맞지 않아요")
                String password) {}
