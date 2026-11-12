package com.hoperefund.income.mapper;

import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * INCOME 테이블 조회·저장 (SQL은 resources/mapper/income/IncomeMapper.xml)
 *
 * <p>메서드 이름 = XML의 id. MyBatis가 이 인터페이스의 구현을 자동으로 만들어 준다.
 */
@Mapper // MyBatis에게 "이 인터페이스는 SQL 매퍼"라고 알려 주는 표시
public interface IncomeMapper {

    /**
     * 회원 · 연도로 소득 1행 조회
     *
     * @return 없으면 null
     */
    // @Param("이름"): XML에서 #{이름}으로 이 값을 꺼내 쓸 수 있게 이름을 붙인다
    IncomeResponseDTO findByMemberAndYear(
            @Param("memberId") long memberId, @Param("taxYear") int taxYear);

    /**
     * 그 연도 행이 있으면 UPDATE, 없으면 INSERT (MERGE)
     *
     * @return 바뀐 행 수 (1)
     */
    int upsert(
            @Param("memberId") long memberId,
            @Param("taxYear") int taxYear,
            @Param("req") IncomeRequestDTO req);

    // [수정 11/12] 추가 · 이유: 연도 검사를 「코드에 고정한 2025~2026」 → 「DB에 기준값이 있는 연도」로 바꿈
    //   관리자가 DEDUCTION_TYPE에 2027년 기준값을 넣으면 코드를 안 고쳐도 2027년 소득을 저장할 수 있게 (팀원 제안)
    /**
     * 그 연도의 공제 기준값(DEDUCTION_TYPE) 행 수
     *
     * @return 0이면 기준값이 없는 연도 → 소득을 받지 않음
     */
    int countCriteria(@Param("taxYear") int taxYear);
}
