package com.hoperefund.income.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.ConflictException;
import com.hoperefund.common.exception.NoCriteriaException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.mapper.IncomeMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.Map;

/** DB 없이 서비스 규칙만 확인해요 (가짜 매퍼 사용). */
class IncomeServiceTest {

    /** 메모리에만 저장하는 가짜 IncomeMapper */
    static class FakeIncomeMapper implements IncomeMapper {
        final Map<String, IncomeResponseDTO> rows = new HashMap<>();
        int upsertCount = 0;
        boolean duplicate = false; // true면 동시 저장 충돌(UQ_INCOME_MEMBER_YEAR 위반)을 흉내 내요

        @Override
        public IncomeResponseDTO findByMemberAndYear(long memberId, int taxYear) {
            return rows.get(memberId + "-" + taxYear);
        }

        @Override
        public int upsert(long memberId, int taxYear, IncomeRequestDTO req) {
            upsertCount++;
            if (duplicate) {
                throw new DuplicateKeyException("ORA-00001");
            }
            IncomeResponseDTO r = new IncomeResponseDTO();
            r.setTaxYear(taxYear);
            r.setTotalSalary(req.getTotalSalary());
            r.setPensionSaving(req.getPensionSaving());
            r.setIrpSaving(req.getIrpSaving());
            rows.put(memberId + "-" + taxYear, r);
            return 1;
        }

        /** 기준값은 2025 · 2026년만 있다고 봐요 (공용 DB와 같게) */
        @Override
        public int countCriteria(int taxYear) {
            return taxYear == 2025 || taxYear == 2026 ? 1 : 0;
        }
    }

    private FakeIncomeMapper mapper;
    private IncomeService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeIncomeMapper();
        service = new IncomeService(mapper);
    }

    private IncomeRequestDTO request(int dependents, int children) {
        IncomeRequestDTO req = new IncomeRequestDTO();
        req.setTotalSalary(36_000_000L);
        req.setWithheldTax(1_523_000L);
        req.setDependents(dependents);
        req.setChildren(children);
        req.setPensionSaving(6_000_000L);
        req.setIrpSaving(3_000_000L);
        return req;
    }

    @Test
    void 저장하면_연금저축과_IRP가_따로_저장된다() {
        // Given
        IncomeRequestDTO req = request(1, 0);
        // When
        IncomeResponseDTO saved = service.save(1L, 2026, req);
        // Then
        assertEquals(3_000_000L, saved.getIrpSaving().longValue());
    }

    @Test
    void 자녀_수가_부양가족_수보다_많으면_저장하지_않는다() {
        // Given
        IncomeRequestDTO req = request(1, 2);
        // When
        assertThrows(BadRequestException.class, () -> service.save(1L, 2026, req));
        // Then
        assertEquals(0, mapper.upsertCount);
    }

    @Test
    void 기준값이_없는_연도는_저장하지_않는다() {
        // Given
        IncomeRequestDTO req = request(0, 0);
        // When · Then
        assertThrows(NoCriteriaException.class, () -> service.save(1L, 2024, req));
    }

    @Test
    void 같은_소득을_동시에_저장해_겹치면_충돌_오류() {
        // Given
        mapper.duplicate = true;
        IncomeRequestDTO req = request(0, 0);
        // When · Then
        assertThrows(ConflictException.class, () -> service.save(1L, 2026, req));
    }

    @Test
    void 저장한_적_없는_연도를_조회하면_없음_오류() {
        // When · Then
        assertThrows(NotFoundException.class, () -> service.get(1L, 2026));
    }
}
