package com.hoperefund.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 분류 · 결제수단별 합계 1줄 (GET /api/expense-totals · F-09)
 *
 * <p>JSON 예: {"categoryId": 2, "categoryCode": "TRANSIT", "categoryName": "대중교통", "payMethod":
 * "CREDIT", "totalAmount": 1330820, "count": 615}
 *
 * <p>왜 「분류 + 결제수단」으로 나누나: 계산 서버(POST /calculate)가 카드 공제를 계산할 때 이 단위로 받기 때문
 * (calc/app/schemas/calculation.py의 cardSpends = [{categoryId, payMethod, amount}]). 신용카드 15% · 체크카드 ·
 * 현금영수증 30%처럼 결제수단마다 공제율이 달라서 분류만으로는 계산할 수 없음.
 *
 * <p>→ 계산 요청을 만들 때는 이 목록을 그대로 {categoryId, payMethod, amount = totalAmount}로 옮기면 됨
 *
 * <p>→ 화면의 「분류별 합계 카드」는 같은 categoryCode끼리 더해서 보여 주면 됨
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseTotalDTO {

    private Long categoryId; // 분류 번호 (계산 서버가 받는 값)
    private String categoryCode; // 분류 코드 (화면 · 로직에서 쓰는 값)
    private String categoryName; // 화면에 보여 줄 분류 이름
    private String payMethod; // CREDIT · CHECK · CASH
    private Long totalAmount; // 금액 합계 (취소 건 음수 포함)
    private Integer count; // 건수
}
