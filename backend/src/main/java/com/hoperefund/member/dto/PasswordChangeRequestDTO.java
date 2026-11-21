package com.hoperefund.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 로그인한 회원의 비밀번호 변경 요청 본문 (F-01-04).
 * 현재 비밀번호가 맞아야 바꾼다. 새 비밀번호 규칙은 회원가입(MemberSignupRequestDTO)과 같다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"currentPassword", "newPassword", "newPasswordConfirm"}) // 로그에 비밀번호가 찍히지 않게
public class PasswordChangeRequestDTO {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요")
    private String currentPassword;

    // 원문은 저장하지 않고 BCrypt 로 암호화한다 (BCrypt 입력 한도 72바이트)
    @NotBlank(message = "새 비밀번호를 입력해 주세요")
    @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 입력해 주세요")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인을 입력해 주세요")
    private String newPasswordConfirm;
}
