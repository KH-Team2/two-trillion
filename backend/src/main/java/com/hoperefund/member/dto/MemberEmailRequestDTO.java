package com.hoperefund.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 아이디 찾기 · 비밀번호 재설정 코드 요청 본문 (F-01-03). 가입한 이메일만 받는다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberEmailRequestDTO {

    @NotBlank(message = "이메일을 입력해 주세요")
    @Email(message = "이메일 형식을 확인해 주세요")
    @Size(max = 100, message = "이메일 형식을 확인해 주세요") // MEMBER.EMAIL 칸 길이
    private String email;
}
