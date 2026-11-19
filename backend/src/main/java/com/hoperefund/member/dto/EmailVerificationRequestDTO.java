package com.hoperefund.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 이메일 인증번호 발송 요청 본문. 인증번호를 받을 이메일만 받는다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationRequestDTO {

    @NotBlank(message = "이메일을 입력해 주세요")
    @Email(message = "이메일 형식을 확인해 주세요")
    @Size(max = 100, message = "이메일 형식을 확인해 주세요") // MEMBER.EMAIL 칸 길이
    private String email;
}
