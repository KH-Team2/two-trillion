package com.hoperefund.income.service;

import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;

/**
 * 소득·공제 정보 조회·저장 (F-06 · F-02-01)
 *
 * <p>인터페이스: "어떤 기능이 있는지"만 적고, 실제 내용은 IncomeServiceImpl에 쓴다.
 */
public interface IncomeService {

    /**
     * 소득 조회
     *
     * @throws com.hoperefund.income.exception.IncomeException 400 연도 오류 · 404 소득 없음
     */
    IncomeResponseDTO getIncome(long memberId, int taxYear);

    /**
     * 소득 저장 (그 연도 행이 있으면 수정, 없으면 새로 등록)
     *
     * @return 저장된 소득
     * @throws com.hoperefund.income.exception.IncomeException 400 연도 오류 · 자녀 수가 부양가족 수보다 많음
     */
    IncomeResponseDTO saveIncome(long memberId, int taxYear, IncomeRequestDTO request);
}
