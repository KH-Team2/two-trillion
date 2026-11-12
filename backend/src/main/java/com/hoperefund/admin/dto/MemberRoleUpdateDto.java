package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberRoleUpdateDto {
	/* F-20: 회원 권한 변경 요청 DTO*/
    private String role; // 변경할 대상 권한 ("USER" 또는 "ADMIN")
}