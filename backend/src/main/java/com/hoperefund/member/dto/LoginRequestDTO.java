package com.hoperefund.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 로그인 요청 본문 (F-01-02).
 * Controller 의 @Valid 가 아래 조건을 검사하고, 어긋나면 400 VALIDATION 으로 응답한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password") // 로그에 비밀번호가 찍히지 않게
public class LoginRequestDTO {

    // 길이 초과도 "로그인 실패"와 같은 문구 → 어떤 아이디가 있는지 알려 주지 않기 위해
    @NotBlank(message = "아이디를 입력해 주세요")
    @Size(max = 50, message = "아이디 또는 비밀번호가 맞지 않아요")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해 주세요")
    @Size(max = 100, message = "아이디 또는 비밀번호가 맞지 않아요")
    private String password;
}
