package com.hoperefund.member.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * MEMBER 테이블 INSERT 전달용 DTO (Service → Mapper)
 * 화면 요청을 검증·정리한 값과 서버가 정한 값을 담는다.
 * memberId는 INSERT 시 MyBatis selectKey가 채운다.
 */
@Getter
@Setter
public class MemberInsertDTO {

    private Long memberId; // MEMBER_ID
    private String loginId; // LOGIN_ID (소문자)
    private String email; // EMAIL (소문자)
    private String password; // PASSWORD (BCrypt 암호문)
    private LocalDate birthDate; // BIRTH_DATE
    private String gender; // GENDER M/F
    private String termsVersion; // TERMS_VERSION
    private String privacyVersion; // PRIVACY_VERSION
    private String financeVersion; // FINANCE_VERSION
    private String emailVerifiedYn; // EMAIL_VERIFIED_YN
    private String dataOrigin; // DATA_ORIGIN
}
