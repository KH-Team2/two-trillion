package com.hoperefund.prediction.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.DeductionDetailDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 가짜 계산(calc.mode=mock)이 진짜 결과처럼 보이지 않는지 확인해요. 운영에서 CALC_MODE=http를 빠뜨려도 화면의 항목별 근거에 「가짜 계산」이 보여서
 * 알아챌 수 있게.
 */
class MockCalcClientTest {

    private IncomeResponseDTO income() {
        IncomeResponseDTO income = new IncomeResponseDTO();
        income.setTotalSalary(36_000_000L);
        income.setWithheldTax(1_523_000L);
        income.setNationalPension(0L);
        income.setHealthInsurance(0L);
        income.setPensionSaving(0L);
        income.setIrpSaving(0L);
        return income;
    }

    @Test
    void 가짜_계산의_모든_항목_근거에_가짜_계산_표시가_있다() {
        CalculationResponseDTO result =
                new MockCalcClient().calculate(2026, income(), "N", List.of());

        assertFalse(result.getDetails().isEmpty());
        for (DeductionDetailDTO detail : result.getDetails()) {
            assertTrue(
                    detail.getSourceNote().startsWith("[가짜 계산"),
                    detail.getTypeCode() + " 근거에 가짜 계산 표시가 없어요: " + detail.getSourceNote());
        }
    }

    @Test
    void 가짜_계산은_세액을_계산하지_않는다() {
        CalculationResponseDTO result =
                new MockCalcClient().calculate(2026, income(), "N", List.of());

        assertEquals(0L, result.getDecidedTax());
        assertEquals(1_523_000L, result.getExpectedRefund());
    }
}
