package com.hoperefund.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 로그인 확인용 MEMBER 1행 (Mapper → Service).
 * 암호화된 비밀번호까지 담고 있으므로 이 객체를 그대로 응답하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "password") // 로그에 비밀번호가 찍히지 않게
public class LoginMemberDTO {

    private Long memberId;
    private String loginId;
    private String password; // BCrypt 암호문
    private String role; // USER · ADMIN
    private String status; // ACTIVE · WITHDRAWN
    private Integer loginFailCount;
    private String lockedYn; // LOCKED_UNTIL 이 지금(DB 시각)보다 뒤면 Y (XML 에서 계산)

    public boolean isLocked() {
        return "Y".equals(lockedYn);
    }
}
