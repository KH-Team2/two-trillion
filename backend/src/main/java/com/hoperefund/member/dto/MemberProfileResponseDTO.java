package com.hoperefund.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 회원정보 조회 · 수정 응답 (F-01-04).
 * 이메일은 일부를 가린 값만 보낸다 (예: ho***@example.com). 권한 · 계정 상태 · 비밀번호는 보내지 않는다.
 */
@Getter
@AllArgsConstructor
public class MemberProfileResponseDTO {

    private final String loginId;
    private final String maskedEmail;
    private final LocalDate birthDate;
    private final String gender; // M · F
    private final int militaryMonths;
    private final LocalDate hireDate; // 없으면 null
    private final String companySize; // SME · MID · LARGE · 없으면 null
    private final String homelessHeadYn; // Y · N
    private final LocalDateTime updatedAt;

    // DB 1행 + 가린 이메일 → 응답 (이메일 원문은 넣지 않는다)
    public static MemberProfileResponseDTO from(MemberProfileDTO member, String maskedEmail) {
        Integer months = member.getMilitaryMonths();
        return new MemberProfileResponseDTO(
                member.getLoginId(),
                maskedEmail,
                member.getBirthDate(),
                member.getGender(),
                months == null ? 0 : months,
                member.getHireDate(),
                member.getCompanySize(),
                member.getHomelessHeadYn(),
                member.getUpdatedAt());
    }
}
