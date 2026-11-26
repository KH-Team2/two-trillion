package com.hoperefund.admin.mapper;

import com.hoperefund.admin.dto.TaxCriteriaRequestDTO;
import com.hoperefund.admin.dto.TaxCriteriaResponseDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/*
- F-19 (F-08-01): 세법 기준값 관리 MyBatis 매퍼 인터페이스
- - DEDUCTION_TYPE, TAX_BRACKET, BENEFIT_CONDITION 조회·추가·수정·연도복제
 */
@Mapper
public interface AdminTaxCriteriaMapper {

    // 귀속 연도별 DEDUCTION_TYPE 목록 조회
    List<TaxCriteriaResponseDTO.DeductionTypeItem> selectDeductionTypes(
            @Param("taxYear") Integer taxYear);

    // 귀속 연도별 TAX_BRACKET 목록 조회
    List<TaxCriteriaResponseDTO.TaxBracketItem> selectTaxBrackets(
            @Param("taxYear") Integer taxYear);

    // 귀속 연도별 BENEFIT_CONDITION 목록 조회
    List<TaxCriteriaResponseDTO.BenefitConditionItem> selectBenefitConditions(
            @Param("taxYear") Integer taxYear);

    // DEDUCTION_TYPE 단건 추가
    int insertDeductionType(
            @Param("dto") TaxCriteriaRequestDTO dto, @Param("updatedBy") Long updatedBy);

    // TAX_BRACKET 단건 추가
    int insertTaxBracket(
            @Param("dto") TaxCriteriaRequestDTO dto, @Param("updatedBy") Long updatedBy);

    // BENEFIT_CONDITION 단건 추가
    int insertBenefitCondition(
            @Param("dto") TaxCriteriaRequestDTO dto, @Param("updatedBy") Long updatedBy);

    // DEDUCTION_TYPE 수정
    int updateDeductionType(
            @Param("dto") TaxCriteriaRequestDTO dto, @Param("updatedBy") Long updatedBy);

    // TAX_BRACKET 수정
    int updateTaxBracket(
            @Param("dto") TaxCriteriaRequestDTO dto, @Param("updatedBy") Long updatedBy);

    // BENEFIT_CONDITION 수정
    int updateBenefitCondition(
            @Param("dto") TaxCriteriaRequestDTO dto, @Param("updatedBy") Long updatedBy);

    // 신규 귀속 연도 생성 시 DEDUCTION_TYPE 복사
    int copyDeductionTypesByYear(
            @Param("sourceYear") Integer sourceYear,
            @Param("targetYear") Integer targetYear,
            @Param("updatedBy") Long updatedBy);

    // 신규 귀속 연도 생성 시 TAX_BRACKET 복사
    int copyTaxBracketsByYear(
            @Param("sourceYear") Integer sourceYear,
            @Param("targetYear") Integer targetYear,
            @Param("updatedBy") Long updatedBy);

    // 신규 귀속 연도 생성 시 BENEFIT_CONDITION 복사
    int copyBenefitConditionsByYear(
            @Param("sourceYear") Integer sourceYear,
            @Param("targetYear") Integer targetYear,
            @Param("updatedBy") Long updatedBy);
}
