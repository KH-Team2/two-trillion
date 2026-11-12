package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaskedMemberResponseDto {
	/* F-20: 마스킹된 회원 목록 조회 DTO */
    private Long memberId;
    private String name;       // 마스킹 처리된 이름
    private String email;      // 마스킹 처리된 이메일
    private String role;       // USER 또는 ADMIN
    private String gender;     // 성별 (M / F / null)
    private String updatedAt;  // 수정일시
}