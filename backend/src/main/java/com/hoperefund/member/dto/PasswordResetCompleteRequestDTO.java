package com.hoperefund.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 비밀번호 재설정 완료 요청 본문 (F-01-03).
 * 메일로 받은 인증 코드와 새 비밀번호를 함께 받는다. 비밀번호 규칙은 회원가입(MemberSignupRequestDTO)과 같다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"code", "newPassword", "newPasswordConfirm"}) // 로그에 코드 · 비밀번호가 찍히지 않게
public class PasswordResetCompleteRequestDTO {

    @NotBlank(message = "이메일을 입력해 주세요")
    @Email(message = "이메일 형식을 확인해 주세요")
    @Size(max = 100, message = "이메일 형식을 확인해 주세요")
    private String email;

    @NotBlank(message = "인증 코드를 입력해 주세요")
    @Pattern(regexp = "^[0-9]{6}$", message = "6자리 인증 코드를 입력해 주세요")
    private String code;

    // 원문은 저장하지 않고 BCrypt 로 암호화한다 (BCrypt 입력 한도 72바이트)
    @NotBlank(message = "새 비밀번호를 입력해 주세요")
    @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 입력해 주세요")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인을 입력해 주세요")
    private String newPasswordConfirm;
}
