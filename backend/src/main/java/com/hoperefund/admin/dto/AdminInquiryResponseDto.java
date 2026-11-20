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
public class AdminInquiryResponseDto {
    /* F-17, F-18: 고객센터 문의 내역 및 답변 DTO */
    private Long inquiryId; // 문의 고유 번호
    private String title; // 문의 제목
    private String content; // 문의 내용
    private String inquiryType; // 문의 유형 (ERROR 오류 / QUESTION 문의 / ETC 기타)

    // [수정 11/20] 주석만 수정 · 이유: DB에 저장되는 실제 값(영문 코드)에 맞춤
    private String status; // 처리 상태 (WAITING 접수 / CHECKING 확인 중 / ANSWERED 답변 완료)

    private LocalDateTime createdAt; // 작성일시

    // 상세 조회 시 노출되는 관리자 답변 관련 필드 (F-18 연계)
    private String answer; // 관리자 답변 내용 (CHECKING이면 null → 화면이 「확인 후 답변 드리겠습니다.」 고정 문구 표시)
    private LocalDateTime answeredAt; // 답변 등록일시
    private Long answererId; // 답변자(관리자) ID
}
