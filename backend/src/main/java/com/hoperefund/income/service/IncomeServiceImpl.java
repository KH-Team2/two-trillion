package com.hoperefund.income.service;

import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.exception.IncomeException;
import com.hoperefund.income.mapper.IncomeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소득 조회·저장의 실제 내용
 *
 * <p>검증 4가지 (API 명세서 · 기능명세서)
 *
 * <p>1. 연도는 2025 · 2026만 → checkTaxYear()
 *
 * <p>2. 총급여 · 기납부세액 필수 → IncomeRequestDTO의 @NotNull
 *
 * <p>3. 모든 금액 0 이상 → IncomeRequestDTO의 @PositiveOrZero
 *
 * <p>4. 자녀 수 ≤ 부양가족 수 → checkChildren() (DB 제약으로는 못 막아서 여기서 확인)
 */
@Slf4j
@Service // 스프링이 이 클래스를 Service로 등록 → Controller에서 IncomeService로 주입받음
@RequiredArgsConstructor // final 칸을 받는 생성자 자동 생성 → 스프링이 IncomeMapper를 넣어 줌
public class IncomeServiceImpl implements IncomeService {

    // 받을 수 있는 귀속 연도 (API 명세서: 2025·2026만)
    // 새 연도를 열 때는 이 두 숫자를 바꾼다
    static final int MIN_TAX_YEAR = 2025;
    static final int MAX_TAX_YEAR = 2026;

    private final IncomeMapper incomeMapper;

    // @Transactional(readOnly = true): 읽기만 하는 작업이라고 알려 줌 (DB가 조금 더 가볍게 처리)
    @Override
    @Transactional(readOnly = true)
    public IncomeResponseDTO getIncome(long memberId, int taxYear) {
        checkTaxYear(taxYear);
        IncomeResponseDTO income = incomeMapper.findByMemberAndYear(memberId, taxYear);
        if (income == null) {
            // 화면은 404를 받으면 빈 입력 폼을 보여 준다
            throw new IncomeException(
                    HttpStatus.NOT_FOUND, "NOT_FOUND", taxYear + "년 소득 정보가 아직 없어요.");
        }
        return income;
    }

    // @Transactional: 이 메서드 안의 DB 작업을 하나로 묶음. 중간에 오류가 나면 전부 취소(롤백)
    @Override
    @Transactional
    public IncomeResponseDTO saveIncome(long memberId, int taxYear, IncomeRequestDTO request) {
        checkTaxYear(taxYear);
        checkChildren(request);
        incomeMapper.upsert(memberId, taxYear, request);
        log.info("소득 저장 member={} year={}", memberId, taxYear); // 금액은 로그에 남기지 않음 (백엔드 규칙)
        // 저장한 값을 다시 읽어서 돌려줌 (DB가 채운 updatedAt · 0으로 바뀐 빈 칸까지 화면에 정확히 보여 주려고)
        return incomeMapper.findByMemberAndYear(memberId, taxYear);
    }

    /** 연도 검사: 2025 · 2026이 아니면 400 */
    private void checkTaxYear(int taxYear) {
        if (taxYear < MIN_TAX_YEAR || taxYear > MAX_TAX_YEAR) {
            throw new IncomeException(
                    HttpStatus.BAD_REQUEST, "NO_CRITERIA", "아직 계산할 수 없는 연도예요.");
        }
    }

    /** 20세 이하 자녀 수가 부양가족 수보다 많으면 400 (비워 보낸 칸은 0으로 봄 · DB에도 0으로 저장되니까) */
    private void checkChildren(IncomeRequestDTO request) {
        int children = request.getChildren() == null ? 0 : request.getChildren();
        int dependents = request.getDependents() == null ? 0 : request.getDependents();
        if (children > dependents) {
            throw new IncomeException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION",
                    "20세 이하 자녀 수는 부양가족 수보다 많을 수 없어요.");
        }
    }
}
