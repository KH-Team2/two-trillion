package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaskedMemberResponseDto {
    /* F-20: 마스킹된 회원 목록 조회 DTO */
    private Long memberId;

    // [삭제 11/23 고은] name(마스킹 이름) · 이유: v3.5부터 이름을 받지 않아 NAME이 비어 있음 (명세 F-08-02 비고 「실명을 받지 않아서 이름
    // 노출 문제는 없어요」)

    private String email; // 마스킹 처리된 이메일 (예: te***@naver.com)
    private String role; // USER 또는 ADMIN

    // [추가 11/23 고은] 계정 상태 ACTIVE(사용 중) / WITHDRAWN(탈퇴)
    //   이유: 명세 F-08-02 ① 「이메일 일부를 가리고 계정 상태 · 권한 이 3가지를 목록으로 표시」
    private String status;

    // [삭제 11/23 고은] gender · 이유: 목록에 보일 3가지(이메일 · 상태 · 권한)가 아님
    //   → 권한 변경 검사용 성별은 AdminMemberRoleTargetDto로 따로 읽어요 (화면으로는 안 나감)

    // [수정 11/23 고은] updatedAt → createdAt(가입일 'YYYY-MM-DD')
    //   이유: 회원 목록에서 「언제 가입했는지」가 더 쓸모 있어서 · 기존 시연 화면도 가입일을 보여 줬어요
    private String createdAt;
}
