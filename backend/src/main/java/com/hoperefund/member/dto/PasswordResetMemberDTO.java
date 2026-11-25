package com.hoperefund.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 아이디 찾기 · 비밀번호 재설정 대상 회원 1행 (Mapper → Service). 이메일로 찾은 MEMBER 의 필요한 칸만 담는다. */
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetMemberDTO {

    private Long memberId;
    private String loginId;
    private String status; // ACTIVE · WITHDRAWN
    private String emailVerifiedYn; // Y · N
}
