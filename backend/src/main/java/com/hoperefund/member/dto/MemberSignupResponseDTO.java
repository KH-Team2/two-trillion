package com.hoperefund.member.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 회원가입 성공 응답 (F-01-01 · API 명세 기준).
 * 비밀번호 같은 민감정보는 넣지 않는다.
 */
@Getter
@AllArgsConstructor
public class MemberSignupResponseDTO {

    private final Long memberId;
    private final String loginId;
    private final String email;
    private final String name; // 실명을 받지 않으므로 항상 null
    private final String role;
    private final LocalDateTime createdAt;
}
