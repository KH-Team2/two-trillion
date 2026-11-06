package com.hoperefund.expense.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CARD_UPLOAD 테이블 한 행(카드 내역 파일 업로드 1건)을 담는 DTO
 * - 파일 정보만 담고, 파일 안의 거래 N건은 ExpenseDTO에 담는다
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // 빌더 방식으로 객체 만들기
public class CardUploadDTO {

    private Long uploadId; // 업로드번호 (PK)
    private Long memberId; // 회원번호 → MEMBER
    private String fileName; // 원본 파일명
    private Integer rowCount; // 읽은 건수 NUMBER(6)
    private LocalDateTime uploadedAt; // 업로드일
    private String dataOrigin; // 데이터 구분 COLLECTED/GENERATED/INPUT
}