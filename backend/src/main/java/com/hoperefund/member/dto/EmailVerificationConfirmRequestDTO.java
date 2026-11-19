package com.hoperefund.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** 이메일 인증번호 확인 요청 본문. 메일로 받은 6자리 숫자를 받는다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "code") // 로그에 인증번호가 찍히지 않게
public class EmailVerificationConfirmRequestDTO {

    @NotBlank(message = "인증 코드를 입력해 주세요")
    @Pattern(regexp = "^[0-9]{6}$", message = "인증 코드가 맞지 않거나 만료됐어요")
    private String code;
}
