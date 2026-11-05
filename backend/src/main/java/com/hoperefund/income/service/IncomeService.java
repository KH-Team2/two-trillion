package com.hoperefund.income.service;

import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.ConflictException;
import com.hoperefund.common.exception.NoCriteriaException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.mapper.IncomeMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncomeService {

    private static final Logger log = LoggerFactory.getLogger(IncomeService.class);

    /** 연도 값 자체가 말이 되는지만 먼저 걸러요 (DB에 엉뚱한 값을 묻지 않게). 실제 계산 가능 여부는 DB 기준값으로 봐요. */
    static final int MIN_TAX_YEAR = 2000;

    static final int MAX_TAX_YEAR = 2100;

    private final IncomeMapper incomeMapper;

    public IncomeService(IncomeMapper incomeMapper) {
        this.incomeMapper = incomeMapper;
    }

    @Transactional(readOnly = true)
    public IncomeResponseDTO get(long memberId, int taxYear) {
        checkTaxYear(taxYear);
        IncomeResponseDTO income = incomeMapper.findByMemberAndYear(memberId, taxYear);
        if (income == null) {
            throw new NotFoundException(taxYear + "년 소득 정보가 아직 없어요");
        }
        return income;
    }

    @Transactional
    public IncomeResponseDTO save(long memberId, int taxYear, IncomeRequestDTO req) {
        checkTaxYear(taxYear);
        if (req.getChildren() > req.getDependents()) {
            throw new BadRequestException("20세 이하 자녀 수는 부양가족 수보다 많을 수 없어요");
        }
        try {
            incomeMapper.upsert(memberId, taxYear, req);
        } catch (DuplicateKeyException e) {
            // 같은 회원 · 연도의 첫 저장이 동시에 두 번 오면 둘 다 INSERT를 하려다 UQ_INCOME_MEMBER_YEAR에 걸려요
            throw new ConflictException("소득 동시 저장 member=" + memberId + " year=" + taxYear, e);
        }
        log.info("소득 저장 member={} year={}", memberId, taxYear); // 금액은 남기지 않아요
        return incomeMapper.findByMemberAndYear(memberId, taxYear);
    }

    /** 기준값(DEDUCTION_TYPE)이 있는 연도만 받아요 → 새 연도 기준값을 넣으면 코드 수정 없이 바로 열려요. */
    private void checkTaxYear(int taxYear) {
        if (taxYear < MIN_TAX_YEAR
                || taxYear > MAX_TAX_YEAR
                || incomeMapper.countCriteria(taxYear) == 0) {
            throw new NoCriteriaException();
        }
    }
}
