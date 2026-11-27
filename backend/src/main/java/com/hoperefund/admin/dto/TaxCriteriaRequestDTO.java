package com.hoperefund.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * F-19 (F-08-01): 세법 기준값 행 추가/수정 요청 DTO
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
 *
 * <p>[수정 11/26 고은]
 * - 수정(PUT)은 「값 칸」만 바꿔요: 연도 · 코드 · 공제 종류 · 카드 분류 · 결제수단 · 구간 종류는 수정 때 무시
 *   이유: 계산 서버가 TAX_YEAR + TYPE_CODE로 행을 찾아요(명세 v3.9) → 코드가 바뀌면 계산이 그 행을 못 찾음
 * - 행 추가(POST)는 같은 코드로 구간을 하나 더 넣을 때 써요 (명세 「구간 추가는 같은 코드로 행을 INSERT」)
 * - 근거(sourceNote) 필수 · 이유: 명세 ② 「수정/행 추가(근거 SOURCE_NOTE 입력)」
 * - 금액 · 나이 · 개월 · 기간은 0 이상 · 이유: 음수는 DB에서 막지 않아 계산이 이상해질 수 있어서
 * - homelessHouseholdYn → homelessHeadReqYn · 이유: 실제 DB 칸 이름이 HOMELESS_HEAD_REQ_YN (원래 이름이면 SQL 오류)
 * - sourceYear 삭제 · 이유: 연도 복사는 따로 TaxCriteriaYearCopyRequestDTO로 나눔 (한 요청이 두 가지 일을 안 하게)
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

    private Long id; // 수정 시 대상 PK (TYPE_ID / BRACKET_ID / BENEFIT_ID) · 행 추가 때는 비워 둠

    // [수정 11/26 고은] @NotNull 삭제 → 행 추가 때만 Service에서 확인 (수정 때는 연도를 안 바꿔서)
    @Min(value = 2000, message = "귀속 연도를 확인해 주세요")
    @Max(value = 2100, message = "귀속 연도를 확인해 주세요")
    private Integer taxYear; // 귀속 연도 (TAX_YEAR) · 행 추가 때만 사용

    @Size(max = 30)
    private String code; // TYPE_CODE / BRACKET_TYPE / BENEFIT_CODE · 행 추가 때만 사용

    @Size(max = 50)
    private String name; // TYPE_NAME / BENEFIT_NAME

    private String deductionKind; // DEDUCTION_KIND (DEDUCTION_TYPE 행 추가 때만)

    // 공제율/세율/감면율 (0 이상 100 이하 허용 · RATE=0인 항목도 정상)
    @DecimalMin(value = "0.0", message = "비율은 0 이상이어야 합니다")
    @DecimalMax(value = "100.0", message = "비율은 100 이하여야 합니다")
    private BigDecimal rate; // RATE / REDUCTION_RATE

    @PositiveOrZero private Long limitAmount; // 한도 금액 (LIMIT_AMOUNT)
    @PositiveOrZero private Long salaryMin; // 급여 구간 시작 (SALARY_MIN · 초과, DEDUCTION_TYPE)
    @PositiveOrZero private Long salaryMax; // 급여 구간 끝 (SALARY_MAX · 이하 · 비우면 상한 없음)
    private Long categoryId; // 지출 분류 ID (CATEGORY_ID · DEDUCTION_TYPE 행 추가 때만)

    @Pattern(regexp = "CREDIT|CHECK|CASH", message = "결제수단을 확인해 주세요")
    private String payMethod; // 결제수단 (PAY_METHOD · DEDUCTION_TYPE 행 추가 때만)

    @PositiveOrZero private Long lowerAmount; // 구간 시작 금액 (LOWER_AMOUNT · 초과, TAX_BRACKET)
    @PositiveOrZero private Long upperAmount; // 구간 끝 금액 (UPPER_AMOUNT · 이하 · 비우면 상한 없음)
    @PositiveOrZero private Long baseAmount; // 구간 시작점까지의 누적액 (BASE_AMOUNT)

    @PositiveOrZero private Integer minAge; // 최소 연령 (MIN_AGE, BENEFIT_CONDITION)
    @PositiveOrZero private Integer maxAge; // 최대 연령 (MAX_AGE)
    @PositiveOrZero private Integer maxMilitaryMonths; // 군 복무 인정 최대 개월 (MAX_MILITARY_MONTHS)

    @Pattern(regexp = "SME|MID|LARGE", message = "회사 규모를 확인해 주세요")
    private String companySize; // 회사 규모 (COMPANY_SIZE · 비우면 조건 없음)

    @PositiveOrZero private Long salaryLimit; // 총급여 조건 (SALARY_LIMIT, BENEFIT_CONDITION)

    // [수정 11/26 고은] homelessHouseholdYn → homelessHeadReqYn (DB 칸 HOMELESS_HEAD_REQ_YN)
    @Pattern(regexp = "Y|N", message = "무주택 세대주 조건은 Y 또는 N이에요")
    private String homelessHeadReqYn; // 무주택 세대주 필요 여부

    @PositiveOrZero private Integer periodYears; // 적용 기간 연수 (PERIOD_YEARS)

    // [수정 11/26 고은] 근거 필수 · 200바이트(한글 약 66자) 이하
    @NotBlank(message = "근거(법 조문 · 신고안내 쪽수)를 입력해 주세요")
    @Size(max = 66, message = "근거는 66자 이하로 입력해 주세요")
    private String sourceNote; // 근거 법령/메모 (SOURCE_NOTE)
}
