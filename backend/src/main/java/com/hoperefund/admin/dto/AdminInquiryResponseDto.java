package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminInquiryResponseDto {
    private Long inquiryId;         // 문의 고유 번호
    private String title;           // 문의 제목
    private String content;         // 문의 내용
    private String inquiryType;     // 문의 유형 (예: 세무/환급, 카드/지출 등)
    private String status;          // 처리 상태 (접수 / 확인 중 / 답변 완료)
    private LocalDateTime createdAt;// 작성일시
    
    // 상세 조회 시 노출되는 관리자 답변 관련 필드 (F-18 연계)
    private String answer;          // 관리자 답변 내용
    private LocalDateTime answeredAt; // 답변 등록일시
    private Long answererId;        // 답변자(관리자) ID
}