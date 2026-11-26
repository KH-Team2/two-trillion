package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.TaxCriteriaRequestDTO;
import com.hoperefund.admin.dto.TaxCriteriaResponseDTO;
import com.hoperefund.admin.mapper.AdminTaxCriteriaMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * F-19 (F-08-01): 세법 기준값 조회·추가·수정 비즈니스 로직
 * - DEDUCTION_TYPE / TAX_BRACKET / BENEFIT_CONDITION 연동
 * - RATE=0 정상 허용 및 계산식·캐시 정책 안내
 */
@Service
@RequiredArgsConstructor
public class AdminTaxCriteriaService {

    // DEDUCTION_TYPE에서 공제율(RATE)이 0이어도 정상인 대표 코드들
    private static final Set<String> ZERO_RATE_CODES =
            Set.of(
                    "CARD_LIMIT_BASE",
                    "CARD_LIMIT_EXTRA",
                    "CARD_LIMIT_CHILD1",
                    "CARD_LIMIT_CHILD2",
                    "PERSON_BASIC",
                    "STANDARD_CREDIT",
                    "PENSION_SAVING_LIMIT",
                    "EARNED_DEDUCTION",
                    "EARNED_CREDIT");

    private final AdminTaxCriteriaMapper adminTaxCriteriaMapper;

    /*
     * 특정 귀속 연도(TAX_YEAR)의 공제항목·세율구간·혜택조건 조회 및 화면 안내 정보 구성
     */
    public TaxCriteriaResponseDTO getTaxCriteria(Integer taxYear) {
        List<TaxCriteriaResponseDTO.DeductionTypeItem> deductions =
                adminTaxCriteriaMapper.selectDeductionTypes(taxYear).stream()
                        .map(
                                item ->
                                        TaxCriteriaResponseDTO.DeductionTypeItem.builder()
                                                .typeId(item.getTypeId())
                                                .taxYear(item.getTaxYear())
                                                .typeCode(item.getTypeCode())
                                                .typeName(item.getTypeName())
                                                .deductionKind(item.getDeductionKind())
                                                .rate(item.getRate())
                                                .limitAmount(item.getLimitAmount())
                                                .salaryMin(item.getSalaryMin())
                                                .salaryMax(item.getSalaryMax())
                                                .categoryId(item.getCategoryId())
                                                .payMethod(item.getPayMethod())
                                                .sourceNote(item.getSourceNote())
                                                .updatedAt(item.getUpdatedAt())
                                                .updatedBy(item.getUpdatedBy())
                                                .rateZeroAllowed(
                                                        isRateZeroAllowed(
                                                                item.getTypeCode(), item.getRate()))
                                                .noteDetail(buildDeductionNote(item.getTypeCode()))
                                                .build())
                        .toList();

        List<TaxCriteriaResponseDTO.TaxBracketItem> brackets =
                adminTaxCriteriaMapper.selectTaxBrackets(taxYear);

        List<TaxCriteriaResponseDTO.BenefitConditionItem> benefits =
                adminTaxCriteriaMapper.selectBenefitConditions(taxYear);

        return TaxCriteriaResponseDTO.builder()
                .taxYear(taxYear)
                .rateZeroGuide(
                        "DEDUCTION_TYPE의 공제율(RATE)이 0인 행은 정상입니다(한도 금액만 사용하는 항목 및 TAX_BRACKET 참조 항목 포함).")
                .bracketFormulaGuide(
                        "TAX_BRACKET 계산식: 기본금액(BASE_AMOUNT) + ((금액 - 구간시작(LOWER_AMOUNT)) * 비율(RATE) / 100), 원 미만 버림")
                .fixedCreditLimitGuide(
                        "근로소득세액공제 한도(74만·66만·50만·20만)는 DB가 아니라 계산 코드에 고정되어 있어 관리자 화면에서 변경할 수 없습니다.")
                .cacheNotice("계산 서버가 기준값을 5분 동안 캐시하므로, 변경/추가 내용은 최대 5분 뒤부터 계산에 반영됩니다.")
                .deductionTypes(deductions)
                .taxBrackets(brackets)
                .benefitConditions(benefits)
                .build();
    }

    /*
     * 신규 귀속 연도 기준값 생성
     * - sourceYear가 지정되면 해당 연도 기준 데이터를 새 taxYear로 복제
     * - 지정되지 않으면 요청 DTO 값 기준으로 단건 추가
     */
    @Transactional
    public void createTaxCriteria(TaxCriteriaRequestDTO dto, Long updatedBy) {
        if (dto.getSourceYear() != null) {
            adminTaxCriteriaMapper.copyDeductionTypesByYear(
                    dto.getSourceYear(), dto.getTaxYear(), updatedBy);
            adminTaxCriteriaMapper.copyTaxBracketsByYear(
                    dto.getSourceYear(), dto.getTaxYear(), updatedBy);
            adminTaxCriteriaMapper.copyBenefitConditionsByYear(
                    dto.getSourceYear(), dto.getTaxYear(), updatedBy);
            return;
        }
        insertByTargetType(dto, updatedBy);
    }

    /*
     * 기존 세법 기준값 수정 (대상 테이블별 업데이트 및 UPDATED_AT/UPDATED_BY 갱신)
     */
    @Transactional
    public void updateTaxCriteria(TaxCriteriaRequestDTO dto, Long updatedBy) {
        if (dto.getId() == null) {
            throw new IllegalArgumentException("수정할 대상 ID가 필요합니다.");
        }
        switch (dto.getTargetType()) {
            case "DEDUCTION_TYPE" -> adminTaxCriteriaMapper.updateDeductionType(dto, updatedBy);
            case "TAX_BRACKET" -> adminTaxCriteriaMapper.updateTaxBracket(dto, updatedBy);
            case "BENEFIT_CONDITION" ->
                    adminTaxCriteriaMapper.updateBenefitCondition(dto, updatedBy);
            default -> throw new IllegalArgumentException("지원하지 않는 기준값 구분입니다.");
        }
    }

    /*
     * TAX_BRACKET 구간 계산 헬퍼: 기본금액 + ((금액 - 구간시작) * 비율 / 100), 원 미만 버림
     */
    public long calculateBracketTax(
            long baseAmount, long amount, long lowerAmount, BigDecimal rate) {
        BigDecimal diff = BigDecimal.valueOf(Math.max(0L, amount - lowerAmount));
        BigDecimal ratePart =
                diff.multiply(rate == null ? BigDecimal.ZERO : rate)
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        return BigDecimal.valueOf(baseAmount).add(ratePart).longValue();
    }

    private void insertByTargetType(TaxCriteriaRequestDTO dto, Long updatedBy) {
        switch (dto.getTargetType()) {
            case "DEDUCTION_TYPE" -> adminTaxCriteriaMapper.insertDeductionType(dto, updatedBy);
            case "TAX_BRACKET" -> adminTaxCriteriaMapper.insertTaxBracket(dto, updatedBy);
            case "BENEFIT_CONDITION" ->
                    adminTaxCriteriaMapper.insertBenefitCondition(dto, updatedBy);
            default -> throw new IllegalArgumentException("지원하지 않는 기준값 구분입니다.");
        }
    }

    private boolean isRateZeroAllowed(String typeCode, BigDecimal rate) {
        if (rate != null && rate.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        return typeCode != null
                && (typeCode.startsWith("CARD_LIMIT") || ZERO_RATE_CODES.contains(typeCode));
    }

    private String buildDeductionNote(String typeCode) {
        if (typeCode == null) {
            return null;
        }
        return switch (typeCode) {
            case "PENSION_SAVING_LIMIT" ->
                    "연금저축 단독 한도(예: 600만) 항목 · 공제율은 PENSION 행(총급여 5,500만 이하 15% / 초과 12%) 기준";
            case "EARNED_DEDUCTION" ->
                    "근로소득공제 한도(예: 2,000만) 항목 · 공제율·구간은 TAX_BRACKET의 EARNED_DEDUCTION 행 참조";
            case "EARNED_CREDIT" ->
                    "근로소득세액공제 항목 · 공제율·구간은 TAX_BRACKET의 EARNED_CREDIT 행 참조 (한도 74만·66만·50만·20만은 계산 코드 고정)";
            default -> null;
        };
    }
}
