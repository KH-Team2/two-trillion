package com.hoperefund.admin.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminInquiryResponseDTO {
    /* F-17, F-18: 고객센터 문의 내역 및 답변 DTO */
    private Long inquiryId; // 문의 고유 번호

    // [추가 11/20] 작성 회원 번호 · 이유: 관리자 목록(F-18)에서 누가 쓴 문의인지 보여 주려고
    //   (매퍼 SQL이 MEMBER_ID AS memberId를 조회하는데 받을 칸이 없었음) · 회원 본인 조회에서는 비워 둠(null)
    private Long memberId;

    private String title; // 문의 제목
    private String content; // 문의 내용
    private String inquiryType; // 문의 유형 (ERROR 오류 / QUESTION 문의 / ETC 기타)

    // [수정 11/20] 주석만 수정 · 이유: DB에 저장되는 실제 값(영문 코드)에 맞춤
    private String status; // 처리 상태 (WAITING 접수 / CHECKING 확인 중 / ANSWERED 답변 완료)

    private LocalDateTime createdAt; // 작성일시

    // 상세 조회 시 노출되는 관리자 답변 관련 필드 (F-18 연계)
    private String answer; // 관리자 답변 내용 (CHECKING이면 null → 화면이 「확인 후 답변 드리겠습니다.」 고정 문구 표시)
    private LocalDateTime answeredAt; // 답변 등록일시
    private Long answererId; // 답변자(관리자) ID · 관리자 목록에서만 채움 (회원 화면에는 관리자 번호를 보내지 않음)
}
