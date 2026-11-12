package com.hoperefund.expense.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 중복 확인용: 이미 저장된 거래를 (사용일 · 가맹점명 · 금액 · 결제수단)으로 묶어 센 결과 1줄
 *
 * <p>04_분류쿼리.sql 0번 쿼리의 결과 모양과 같음
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseGroupCountDTO {

    private LocalDate usedDate; // 사용일
    private String merchantName; // 가맹점명
    private Long amount; // 금액
    private String payMethod; // 결제수단
    private Integer cnt; // 같은 거래가 몇 건 있는지
}
