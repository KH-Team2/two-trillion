package com.hoperefund.member.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원정보 · 자격 정보 수정 요청 본문 (F-01-04 · PUT 이라 모든 칸을 보낸다).
 * 바꿀 수 있는 칸만 받는다. 아이디 · 이메일(D-04) · 생년월일 · 권한(ROLE) · 계정 상태(STATUS)는 받지 않는다.
 * 비밀번호는 현재 비밀번호 확인이 필요해 따로 받는다 (PasswordChangeRequestDTO).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfileRequestDTO {

    // M · F (CK_MEMBER_GENDER · 비울 수 없음)
    @NotBlank(message = "성별을 선택해 주세요")
    @Pattern(regexp = "^[MF]$", message = "성별을 선택해 주세요")
    private String gender;

    // 0 이상 (CK_MEMBER_MILITARY) · 칸 크기 NUMBER(3)
    @NotNull(message = "군 복무 개월 수를 입력해 주세요")
    @Min(value = 0, message = "0 이상으로 입력해 주세요")
    @Max(value = 999, message = "군 복무 개월 수를 확인해 주세요")
    private Integer militaryMonths;

    // yyyy-MM-dd · 비우면 null 로 저장
    private LocalDate hireDate;

    // SME · MID · LARGE (CK_MEMBER_COMPANY_SIZE) · 비우면 null 로 저장
    @Pattern(regexp = "^(SME|MID|LARGE)$", message = "회사 규모를 선택해 주세요")
    private String companySize;

    // Y · N (CK_MEMBER_HOMELESS · 비울 수 없음)
    @NotBlank(message = "무주택 세대주 여부를 선택해 주세요")
    @Pattern(regexp = "^[YN]$", message = "무주택 세대주 여부를 선택해 주세요")
    private String homelessHeadYn;
}
