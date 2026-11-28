package com.hoperefund.admin.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/*
 * F-19 (F-08-01): 귀속 연도별 세법 기준값 조회 응답 DTO
 * - ERD DEDUCTION_TYPE, TAX_BRACKET, BENEFIT_CONDITION 기준 구성
 *
 * <p>[수정 11/26 고은]
 * - 안내 문구 4개(rateZeroGuide 등) 삭제 → 화면(tax-criteria-service.js)에 둠 · 이유: 글자는 화면이 갖고, 서버는 데이터만
 * - years(기준값이 있는 연도 목록) 추가 · 이유: 화면의 연도 선택칸을 채우려고
 * - 안쪽 Item 클래스에 @Setter 추가 · @Builder 삭제
 *   이유: MyBatis는 빈 상자를 만든 뒤 setter로 칸을 채워요 → @Builder만 있으면 조회 결과를 못 담아요
 * - updatedAt: LocalDate → 'YYYY-MM-DD HH24:MI' 글자 · 이유: 같은 날 여러 번 고쳐도 순서가 보이게 (명세 ① 「마지막 수정: 날짜 · 관리자」)
 *   관리자는 다른 관리자 화면(F-18 · F-21)처럼 번호만 보여요
 * - rateZeroAllowed · noteDetail 삭제 · 이유: RATE 0이 정상인 이유는 화면 안내 한 줄로 충분 (코드 목록을 서버에 따로 두면 기준값이 바뀔 때 같이 고쳐야 함)
 */
@Getter
@Builder
public class TaxCriteriaResponseDTO {

    private Integer taxYear;
    private List<Integer> years; // [추가 11/26 고은] 기준값이 있는 연도 (최신순)

    private List<DeductionTypeItem> deductionTypes;
    private List<TaxBracketItem> taxBrackets;
    private List<BenefitConditionItem> benefitConditions;

    @Getter
    @Setter
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
        private String categoryName; // [추가 11/26 고은] 카드 분류 이름 (전통시장 등)
        private String payMethod;
        private String sourceNote;
        private String updatedAt;
        private Long updatedBy; // 수정한 관리자 번호 (NULL = 스크립트로 넣음)
    }

    @Getter
    @Setter
    public static class TaxBracketItem {
        private Long bracketId;
        private Integer taxYear;
        private String bracketType;
        private Long lowerAmount;
        private Long upperAmount;
        private BigDecimal rate;
        private Long baseAmount;
        private String sourceNote;
        private String updatedAt;
        private Long updatedBy; // 수정한 관리자 번호 (NULL = 스크립트로 넣음)
    }

    @Getter
    @Setter
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
        private String homelessHeadReqYn; // [수정 11/26 고은] DB 칸 이름에 맞춤
        private BigDecimal reductionRate;
        private Long limitAmount;
        private Integer periodYears;
        private String sourceNote;
        private String updatedAt;
        private Long updatedBy; // 수정한 관리자 번호 (NULL = 스크립트로 넣음)
    }
}
