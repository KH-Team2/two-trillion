package com.hoperefund.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 회원가입 요청 DTO (React → Spring Boot)
 * 화면이 넣어야 하는 값만 받는다. 회원번호 · 권한 · 상태 · 동의 시각은 서버가 정한다.
 * 칸 길이는 MEMBER 테이블 기준 (LOGIN_ID 50 · EMAIL 100 · *_VERSION 10).
 */
public record MemberSignupRequestDTO(
        // 아이디: 영문·숫자·밑줄 4~50자 (서버에서 소문자로 바꿔 저장)
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,50}$") String loginId,

        // 이메일 (서버에서 소문자로 바꿔 저장)
        @NotBlank @Email @Size(max = 100) String email,

        // 비밀번호 원문: 서버에서 BCrypt로 암호화 (BCrypt 입력 한도 72바이트)
        @NotBlank @Size(min = 8, max = 72) String password,

        // 비밀번호 확인
        @NotBlank String passwordConfirm,

        // 생년월일 yyyy-MM-dd
        @NotNull @Past LocalDate birthDate,

        // 성별 M/F
        @NotBlank @Pattern(regexp = "^[MF]$") String gender,

        // 필수 동의 (이용약관 · 개인정보 · 금융 정보 저장)
        @NotNull @AssertTrue Boolean termsAgreed,
        @NotNull @AssertTrue Boolean privacyAgreed,
        @NotNull @AssertTrue Boolean financeAgreed,

        // 동의한 동의서 버전 (화면의 VITE_*_VERSION 값)
        @NotBlank @Size(max = 10) String termsVersion,
        @NotBlank @Size(max = 10) String privacyVersion,
        @NotBlank @Size(max = 10) String financeVersion) {

    // 비밀번호 원문이 로그에 찍히지 않도록 toString에서 제외
    @Override
    public String toString() {
        return "MemberSignupRequestDTO[loginId=" + loginId + "]";
    }
}
