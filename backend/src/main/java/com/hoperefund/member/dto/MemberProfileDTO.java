package com.hoperefund.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원정보 조회 1행 (Mapper → Service · F-01-04).
 * 이메일 원문이 들어 있어 화면으로 그대로 보내지 않는다 → Service 가 가려서 MemberProfileResponseDTO 로 바꾼다.
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberProfileDTO {

    private String loginId;
    private String email;
    private LocalDate birthDate;
    private String gender; // M · F
    private Integer militaryMonths;
    private LocalDate hireDate; // 없으면 null
    private String companySize; // SME · MID · LARGE · 없으면 null
    private String homelessHeadYn; // Y · N
    private LocalDateTime updatedAt;
}
