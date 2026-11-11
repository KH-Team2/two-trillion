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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

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
    @DisplayName("저장하면 연금저축과 IRP가 따로 저장된다")
    void savesPensionSavingAndIrpSeparately() {
        // Given
        IncomeRequestDTO req = request(1, 0);
        // When
        IncomeResponseDTO saved = service.save(1L, 2026, req);
        // Then
        assertEquals(3_000_000L, saved.getIrpSaving().longValue());
    }

    @Test
    @DisplayName("자녀 수가 부양가족 수보다 많으면 저장하지 않는다")
    void rejectsWhenChildrenExceedDependents() {
        // Given
        IncomeRequestDTO req = request(1, 2);
        // When
        assertThrows(BadRequestException.class, () -> service.save(1L, 2026, req));
        // Then
        assertEquals(0, mapper.upsertCount);
    }

    @Test
    @DisplayName("기준값이 없는 연도는 저장하지 않는다")
    void rejectsYearWithoutCriteria() {
        // Given
        IncomeRequestDTO req = request(0, 0);
        // When · Then
        assertThrows(NoCriteriaException.class, () -> service.save(1L, 2024, req));
    }

    @Test
    @DisplayName("같은 소득을 동시에 저장해 겹치면 충돌 오류")
    void throwsConflictOnConcurrentSave() {
        // Given
        mapper.duplicate = true;
        IncomeRequestDTO req = request(0, 0);
        // When · Then
        assertThrows(ConflictException.class, () -> service.save(1L, 2026, req));
    }

    @Test
    @DisplayName("저장한 적 없는 연도를 조회하면 없음 오류")
    void throwsNotFoundWhenIncomeMissing() {
        // When · Then
        assertThrows(NotFoundException.class, () -> service.get(1L, 2026));
    }
}
