package com.hoperefund.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SPEND_CATEGORY 테이블 한 행(지출 분류 항목 5개 중 하나)을 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // 빌더 방식으로 객체 만들기
public class SpendCategoryDTO {

    private Long categoryId; // 분류번호 (PK)
    private String categoryCode; // GENERAL/TRANSIT/MARKET/CULTURE/EXCLUDED
    private String categoryName; // 화면 표시 이름
    private String useYn; // 사용 여부 'Y'/'N'
}