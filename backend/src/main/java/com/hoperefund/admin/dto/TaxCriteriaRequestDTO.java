package com.hoperefund.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * F-19 (F-08-01): 세법 기준값 추가/수정 요청 DTO
 *
 * <p>대상 테이블 구분(targetType):
 * - DEDUCTION_TYPE: 공제율(RATE) · 한도(LIMIT_AMOUNT) · 급여구간(SALARY_MIN/SALARY_MAX) 등
 * - TAX_BRACKET: 구간 시작/끝(LOWER_AMOUNT/UPPER_AMOUNT) · 공제율(RATE) · 기본금액(BASE_AMOUNT)
 * - BENEFIT_CONDITION: 청년 혜택 조건(MIN_AGE, MAX_AGE, COMPANY_SIZE, SALARY_LIMIT, REDUCTION_RATE 등)
 *
 * <p>주의사항:
 * - DEDUCTION_TYPE에서 RATE = 0은 정상 허용(LIMIT_AMOUNT만 쓰거나 TAX_BRACKET 참조 항목 등)
 * - 근로소득세액공제 한도(74만·66만·50만·20만)는 계산 코드 고정값이므로 DB 기준값으로 수정하지 않음
 * - 계산 서버 캐시(5분)로 인해 변경 사항 반영까지 최대 5분 소요 가능
 */
@Getter
@Setter
@NoArgsConstructor
public class TaxCriteriaRequestDTO {

    // @Pattern: 대상 기준 테이블 구분(DEDUCTION_TYPE / TAX_BRACKET / BENEFIT_CONDITION)
    @NotBlank(message = "기준값 구분을 선택해 주세요")
    @Pattern(
            regexp = "DEDUCTION_TYPE|TAX_BRACKET|BENEFIT_CONDITION",
            message = "기준값 구분은 DEDUCTION_TYPE, TAX_BRACKET, BENEFIT_CONDITION 중 하나여야 합니다")
    private String targetType; // 기준값 대상 구분

    private Long id; // 수정 시 대상 PK (TYPE_ID / BRACKET_ID / BENEFIT_ID)

    @NotNull(message = "귀속 연도를 입력해 주세요")
    private Integer taxYear; // 귀속 연도 (TAX_YEAR)

    private String code; // TYPE_CODE / BRACKET_TYPE / BENEFIT_CODE
    private String name; // TYPE_NAME / BENEFIT_NAME
    private String deductionKind; // DEDUCTION_KIND (DEDUCTION_TYPE 전용)

    // 공제율/세율/감면율 (0 이상 100 이하 허용 · RATE=0인 항목도 정상)
    @DecimalMin(value = "0.0", message = "비율은 0 이상이어야 합니다")
    @DecimalMax(value = "100.0", message = "비율은 100 이하여야 합니다")
    private BigDecimal rate; // RATE / REDUCTION_RATE

    private Long limitAmount; // 한도 금액 (LIMIT_AMOUNT)
    private Long salaryMin; // 급여 하한 (SALARY_MIN, DEDUCTION_TYPE)
    private Long salaryMax; // 급여 상한 (SALARY_MAX, DEDUCTION_TYPE)
    private Long categoryId; // 지출 분류 ID (CATEGORY_ID, DEDUCTION_TYPE)
    private String payMethod; // 결제수단 (PAY_METHOD: CREDIT / CHECK / CASH)

    private Long lowerAmount; // 구간 시작 금액 (LOWER_AMOUNT, TAX_BRACKET)
    private Long upperAmount; // 구간 끝 금액 (UPPER_AMOUNT, TAX_BRACKET)
    private Long baseAmount; // 구간 기본금액 (BASE_AMOUNT, TAX_BRACKET)

    private Integer minAge; // 최소 연령 (MIN_AGE, BENEFIT_CONDITION)
    private Integer maxAge; // 최대 연령 (MAX_AGE, BENEFIT_CONDITION)
    private Integer maxMilitaryMonths; // 최대 군 복무 인정 개월 (MAX_MILITARY_MONTHS)
    private String companySize; // 회사 규모 (COMPANY_SIZE: SME / MID / LARGE)
    private Long salaryLimit; // 급여 상한 (SALARY_LIMIT, BENEFIT_CONDITION)
    private String homelessHouseholdYn; // 무주택 세대주 여부 (HOMELESS_HOUSEHOLD_YN: Y / N)
    private Integer periodYears; // 적용 기간 연수 (PERIOD_YEARS, BENEFIT_CONDITION)

    private String sourceNote; // 근거 법령/메모 (SOURCE_NOTE)
    private Integer sourceYear; // 신규 연도 생성 시 복사 기준 연도(선택)
}
