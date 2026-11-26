package com.hoperefund.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberRoleUpdateDto {
    /* F-20: 회원 권한 변경 요청 DTO*/

    // [수정 11/23 고은] @NotBlank · @Pattern 추가
    //   이유: 원래는 "MANAGER" 같은 값도 그대로 DB까지 가서 ORA-02290(CK_MEMBER_ROLE) → 500
    //   → 이제 Controller의 @Valid가 먼저 막아서 400 (「요청 값을 확인해 주세요」)
    @NotBlank
    @Pattern(regexp = "USER|ADMIN") // 정규식: USER 또는 ADMIN 둘 중 하나만
    private String role; // 변경할 대상 권한 ("USER" 또는 "ADMIN")
}
