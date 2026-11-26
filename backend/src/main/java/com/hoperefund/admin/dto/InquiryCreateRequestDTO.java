package com.hoperefund.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * F-17: 회원이 문의를 새로 쓸 때 화면에서 받는 값 (POST /api/inquiries)
 *
 * <p>[신규 11/20] 이유: F-17 「문의 작성」 API가 없어서 추가.
 *
 * <p>받는 칸은 화면이 넣어야 하는 3칸(유형 · 제목 · 내용)만 둔다. 회원번호는 로그인 토큰에서, 상태(WAITING) · 작성일은 DB 기본값으로
 * 정해지므로 요청으로 바꿀 수 없게 칸을 만들지 않는다 (백엔드 규칙).
 *
 * <p>▼ 검사 어노테이션: 컨트롤러에서 {@code @Valid}를 붙이면 스프링이 Service에 넘기기 전에 자동으로 검사한다. 어기면 400 +
 * message 문구가 화면에 그대로 나간다 (GlobalExceptionHandler).
 */
@Getter
@Setter
@NoArgsConstructor
public class InquiryCreateRequestDTO {

    // @NotBlank: null · 빈 글자("") · 띄어쓰기만(" ") 모두 막음
    // @Pattern: 글자 모양 검사. "ERROR|QUESTION|ETC" = 셋 중 하나만 통과 (| 는 "또는")
    //   DB CK_INQUIRY_TYPE 규칙과 같은 값 · 화면의 오류/문의/기타 → ERROR/QUESTION/ETC (D-12)
    @NotBlank(message = "문의 유형을 선택해 주세요")
    @Pattern(regexp = "ERROR|QUESTION|ETC", message = "문의 유형은 오류 · 문의 · 기타 중에서 골라 주세요")
    private String inquiryType;

    // @Size(max = 66): 글자 수 66자까지 · DB 칸이 200바이트(한글 1자 = 3바이트)라 한글 66자가 최대 (기능명세서 F-07-01)
    @NotBlank(message = "제목을 입력해 주세요")
    @Size(max = 66, message = "제목은 66자 이내로 입력해 주세요")
    private String title;

    @NotBlank(message = "문의 내용을 입력해 주세요")
    private String content;
}
