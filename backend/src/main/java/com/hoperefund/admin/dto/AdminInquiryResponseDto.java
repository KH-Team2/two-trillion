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
    private String inquiryType; // 문의 유형 (예: 세무/환급, 카드/지출 등)
    private String status; // 처리 상태 (접수 / 확인 중 / 답변 완료)
    private LocalDateTime createdAt; // 작성일시

    // 상세 조회 시 노출되는 관리자 답변 관련 필드 (F-18 연계)
    private String answer; // 관리자 답변 내용
    private LocalDateTime answeredAt; // 답변 등록일시
    private Long answererId; // 답변자(관리자) ID
}
