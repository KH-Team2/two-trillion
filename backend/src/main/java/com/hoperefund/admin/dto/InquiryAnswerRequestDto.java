package com.hoperefund.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InquiryAnswerRequestDto {
    /* F-18: 관리자 [확인 중] 버튼 · 답변 등록 요청 DTO (PUT /api/admin/inquiries/{id}/answer) */

    // [수정 11/20] 상태값을 한글(접수/확인 중/답변 완료) → DB 코드(WAITING/CHECKING/ANSWERED)로 · @NotBlank · @Pattern
    // 추가
    //   이유(PR #44 리뷰): DB CK_INQUIRY_STATUS는 영문 코드만 받아서 한글이 오면 DB가 거부함
    //   @Pattern: 셋 중 하나가 아니면 Service까지 오기 전에 400으로 막음 (컨트롤러의 @Valid가 실행)
    @NotBlank(message = "변경할 상태를 선택해 주세요")
    @Pattern(regexp = "WAITING|CHECKING|ANSWERED", message = "상태는 접수 · 확인 중 · 답변 완료 중 하나예요")
    private String status; // 변경할 처리 상태 (WAITING 접수 / CHECKING 확인 중 / ANSWERED 답변 완료)

    // 관리자 답변 내용 · ANSWERED(답변 완료)일 때만 필수 (빈칸 검사는 Service에서)
    // [수정 11/20] 주석의 「임시 안내」 삭제 · 이유: 확인 중일 때 임시 안내 답변은 화면 고정 문구라 DB에 저장하지 않음 (D-22)
    private String answer;

    // [삭제 11/20] private String adminid; // 답변을 등록하는 관리자 ID
    //   이유(PR #44 리뷰): 답변자는 「로그인한 관리자」라 요청 본문이 아니라 토큰(@AuthenticationPrincipal)에서 받아야 함
    //   (본문으로 받으면 다른 관리자 번호를 넣어 보낼 수 있음) · 또 String이었는데 DB ANSWERED_BY는 NUMBER
}
