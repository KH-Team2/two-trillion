package com.hoperefund.income.mapper;

import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** INCOME 테이블. SQL은 resources/mapper/income/IncomeMapper.xml. 모든 SQL에 MEMBER_ID 조건이 있어요. */
@Mapper
public interface IncomeMapper {

    /** 회원 + 귀속 연도로 1행 조회 (없으면 null). */
    IncomeResponseDTO findByMemberAndYear(
            @Param("memberId") long memberId, @Param("taxYear") int taxYear);

    /** 회원 + 귀속 연도 행이 있으면 고치고, 없으면 새로 넣어요 (UQ_INCOME_MEMBER_YEAR). */
    int upsert(
            @Param("memberId") long memberId,
            @Param("taxYear") int taxYear,
            @Param("req") IncomeRequestDTO req);

    /** 그 연도 공제 기준값(DEDUCTION_TYPE) 행 수. 0이면 계산할 수 없는 연도예요. */
    int countCriteria(@Param("taxYear") int taxYear);
}
