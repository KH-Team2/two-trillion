package com.hoperefund.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 분류별 거래 건수 1줄 (업로드 결과 화면의 「분류별 건수」)
 *
 * <p>JSON 예: {"categoryCode": "TRANSIT", "categoryName": "대중교통", "count": 3}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryCountDTO {

    private String categoryCode; // 분류 코드
    private String categoryName; // 화면에 보여 줄 이름
    private int count; // 건수
}
