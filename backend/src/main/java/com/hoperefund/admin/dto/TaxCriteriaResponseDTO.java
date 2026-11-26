package com.hoperefund.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/*
 * F-19 (F-08-01): 귀속 연도별 세법 기준값 조회 응답 DTO
 * - ERD DEDUCTION_TYPE, TAX_BRACKET, BENEFIT_CONDITION 기준 구성
 * - 화면 안내용 정책 문구(RATE=0 정상 안내, 계산식, 고정 한도 미수정, 5분 캐시 반영) 포함
 */
@Getter
@Builder
public class TaxCriteriaResponseDTO {

    private Integer taxYear;
    private String rateZeroGuide;
    private String bracketFormulaGuide;
    private String fixedCreditLimitGuide;
    private String cacheNotice;

    private List<DeductionTypeItem> deductionTypes;
    private List<TaxBracketItem> taxBrackets;
    private List<BenefitConditionItem> benefitConditions;

    @Getter
    @Builder
    public static class DeductionTypeItem {
        private Long typeId;
        private Integer taxYear;
        private String typeCode;
        private String typeName;
        private String deductionKind;
        private BigDecimal rate;
        private Long limitAmount;
        private Long salaryMin;
        private Long salaryMax;
        private Long categoryId;
        private String payMethod;
        private String sourceNote;
        private LocalDate updatedAt;
        private Long updatedBy;
        private boolean rateZeroAllowed;
        private String noteDetail;
    }

    @Getter
    @Builder
    public static class TaxBracketItem {
        private Long bracketId;
        private Integer taxYear;
        private String bracketType;
        private Long lowerAmount;
        private Long upperAmount;
        private BigDecimal rate;
        private Long baseAmount;
        private String sourceNote;
        private LocalDate updatedAt;
        private Long updatedBy;
    }

    @Getter
    @Builder
    public static class BenefitConditionItem {
        private Long benefitId;
        private Integer taxYear;
        private String benefitCode;
        private String benefitName;
        private Integer minAge;
        private Integer maxAge;
        private Integer maxMilitaryMonths;
        private String companySize;
        private Long salaryLimit;
        private String homelessHouseholdYn;
        private BigDecimal reductionRate;
        private Long limitAmount;
        private Integer periodYears;
        private String sourceNote;
        private LocalDate updatedAt;
        private Long updatedBy;
    }
}
