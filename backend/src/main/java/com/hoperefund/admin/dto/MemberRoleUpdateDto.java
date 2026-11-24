package com.hoperefund.admin.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * F-20: 관리자가 회원의 권한(USER <-> ADMIN)을 변경할 때 받는 요청 DTO
 *       (PUT /api/admin/members/{id}/role)
 *
 * <p>대상 회원 ID는 경로 변수({id})로, 변경을 수행하는 관리자 ID는 인증 정보로 받으므로
 * 요청 본문에는 변경할 목표 권한(role)만 둔다.
 *
 * <p>컨트롤러에서 {@code @Valid}를 붙이면 Service 진입 전에 검증하며,
 * 규칙과 다르면 400 응답과 함께 message 문구가 반환된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberRoleUpdateDto {

    // @Pattern: 허용된 권한 코드(USER, ADMIN) 형식인지 검사 (DB MEMBER.ROLE 값 기준)
    @Pattern(regexp = "USER|ADMIN", message = "권한은 USER 또는 ADMIN만 입력할 수 있어요")
    private String role; // 변경할 대상 권한 (USER / ADMIN · DB MEMBER.ROLE)
}
