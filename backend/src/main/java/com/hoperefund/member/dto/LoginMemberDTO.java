package com.hoperefund.member.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 로그인 확인용 회원 조회 DTO (Mapper → Service)
 * 화면에 돌려주지 않는다 (비밀번호 암호문 포함).
 */
@Getter
@Setter
public class LoginMemberDTO {

    private Long memberId; // MEMBER_ID
    private String loginId; // LOGIN_ID
    private String password; // PASSWORD (BCrypt 암호문)
    private String role; // ROLE USER/ADMIN
    private String status; // STATUS ACTIVE/WITHDRAWN
    private Integer loginFailCount; // LOGIN_FAIL_COUNT
    private String lockedYn; // LOCKED_UNTIL이 지금(DB 시각)보다 뒤면 Y

    public boolean isLocked() {
        return "Y".equals(lockedYn);
    }
}
