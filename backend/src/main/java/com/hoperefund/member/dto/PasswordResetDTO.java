package com.hoperefund.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * PASSWORD_RESET 1행 (Service ↔ Mapper).
 * 저장할 때는 memberId · codeHash · validMinutes 를 넣고, 조회할 때는 상태 칸이 채워진다.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "codeHash") // 로그에 코드 해시가 찍히지 않게
public class PasswordResetDTO {

    private Long resetId; // INSERT 할 때 XML 의 selectKey 가 채운다
    private Long memberId;
    private String codeHash; // 인증 코드 BCrypt 해시 (원문 저장 X)
    private Integer validMinutes; // 저장할 때만: 지금(DB 시각)부터 몇 분 유효한지
    private String usedYn; // Y = 이미 사용
    private Integer failCount; // 틀린 횟수
    private String expiredYn; // EXPIRES_AT 이 지금(DB 시각) 이전이면 Y (XML 에서 계산)

    public boolean isUsed() {
        return "Y".equals(usedYn);
    }

    public boolean isExpired() {
        return "Y".equals(expiredYn);
    }
}
