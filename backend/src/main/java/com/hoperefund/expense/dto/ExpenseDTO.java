package com.hoperefund.expense.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EXPENSE 테이블 한 행(카드 거래 1건 + 분류 결과)을 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // 빌더 방식으로 객체 만들기
public class ExpenseDTO {

    private Long expenseId; // 지출번호 (PK)
    private Long memberId; // 회원번호 → MEMBER
    private Long uploadId; // 업로드번호 → CARD_UPLOAD
    private LocalDateTime usedDate; // 사용일 (승인일시라 시·분·초까지 담으려고 LocalDateTime)
    private String merchantName; // 가맹점명
    private String payMethod; // 결제수단 CREDIT(신용)/CHECK(체크)/CASH(현금영수증)
    private Long amount; // 금액 (원)
    private Long categoryId; // 분류 결과 → SPEND_CATEGORY
    private Long listId; // 일치한 목록 행 → MERCHANT_LIST (일반 분류면 NULL)
}