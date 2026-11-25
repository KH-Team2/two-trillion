package com.hoperefund.member.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 회원 탈퇴 요청 본문 (F-01-05).
 * D-05: 탈퇴할 때 비밀번호를 다시 확인한다 → 토큰을 도용당해도 비밀번호를 모르면 탈퇴시킬 수 없게.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password") // 로그에 비밀번호가 찍히지 않게
public class MemberWithdrawalRequestDTO {

    @NotBlank(message = "비밀번호를 입력해 주세요")
    private String password;
}
