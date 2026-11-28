package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * [신규 11/23 고은] F-20 권한 변경 전에 대상 회원을 확인하는 용도 (화면으로 보내지 않아요)
 *
 * <p>원래는 목록용 MaskedMemberResponseDto로 같이 읽었는데, 목록에서 성별을 뺐기 때문에
 * 검사에 필요한 칸(권한 · 상태 · 성별)만 담는 상자를 따로 만들었어요.
 */
@Getter
@Setter
public class AdminMemberRoleTargetDto {
    private Long memberId;
    private String role; // USER / ADMIN
    private String status; // ACTIVE / WITHDRAWN
    private String gender; // M / F / null(성별 없는 관리자)
}
