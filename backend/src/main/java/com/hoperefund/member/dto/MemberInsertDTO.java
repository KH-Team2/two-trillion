package com.hoperefund.member.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * MEMBER 테이블에 넣을 값 (Service → Mapper).
 * 화면 요청을 검사 · 정리한 값과 서버가 정한 값을 함께 담는다.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "password") // 로그에 비밀번호가 찍히지 않게
public class MemberInsertDTO {

    private Long memberId; // INSERT 할 때 XML 의 selectKey 가 채운다
    private String loginId; // 소문자
    private String email; // 소문자
    private String password; // BCrypt 암호문
    private LocalDate birthDate;
    private String gender; // M · F
    private String termsVersion;
    private String privacyVersion;
    private String financeVersion;
    private String emailVerifiedYn; // 인증을 마치고 가입했으면 Y
    private String dataOrigin; // 화면에서 입력한 데이터 = INPUT
}
