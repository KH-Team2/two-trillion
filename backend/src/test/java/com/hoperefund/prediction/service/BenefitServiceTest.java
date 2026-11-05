package com.hoperefund.prediction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.service.IncomeService;
import com.hoperefund.prediction.client.MockCalcClient;
import com.hoperefund.prediction.dto.BenefitResponseDTO;
import com.hoperefund.prediction.dto.MemberQualificationDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB 없이 혜택 진단 흐름만 확인해요 (가짜 매퍼 + 가짜 계산). */
class BenefitServiceTest {

    private CalculationServiceTest.FakeIncomeMapper incomeMapper;
    private CalculationServiceTest.FakePredictionMapper predictionMapper;
    private BenefitService service;

    @BeforeEach
    void setUp() {
        incomeMapper = new CalculationServiceTest.FakeIncomeMapper();
        predictionMapper = new CalculationServiceTest.FakePredictionMapper();
        service =
                new BenefitService(
                        new IncomeService(incomeMapper), new MockCalcClient(), predictionMapper);
    }

    private void saveIncome(long memberId, int taxYear) {
        IncomeResponseDTO income = new IncomeResponseDTO();
        income.setTaxYear(taxYear);
        income.setTotalSalary(30_000_000L);
        incomeMapper.rows.put(memberId + "-" + taxYear, income);
    }

    @Test
    void 소득과_회원_정보가_있으면_혜택_3개를_진단한다() {
        // Given
        saveIncome(1L, 2026);
        predictionMapper.qualification = new MemberQualificationDTO();
        // When
        BenefitResponseDTO response = service.diagnose(1L, 2026);
        // Then
        assertEquals(3, response.getResults().size());
    }

    @Test
    void 소득이_없으면_진단하지_않는다() {
        // When · Then
        assertThrows(NotFoundException.class, () -> service.diagnose(1L, 2026));
    }

    @Test
    void 회원_정보가_없으면_진단하지_않는다() {
        // Given
        saveIncome(1L, 2026);
        // When · Then
        assertThrows(NotFoundException.class, () -> service.diagnose(1L, 2026));
    }
}
