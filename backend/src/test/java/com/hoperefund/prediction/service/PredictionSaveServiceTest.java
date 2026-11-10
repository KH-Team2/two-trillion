package com.hoperefund.prediction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.DeductionDetailDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 결과 저장(PredictionSaveService) 규칙만 따로 확인해요 (DB 없이 가짜 매퍼). 저장 성공 · 기준값 0개는 CalculationServiceTest에서
 * 확인했어요. 여기서는 저장을 멈춰야 하는 경우를 봐요.
 */
class PredictionSaveServiceTest {

    /** 같은 코드 · 연도 · 급여 구간에 기준값이 2개 나오는 가짜 매퍼 (기준값이 잘못 들어간 상황) */
    static class DuplicateTypeMapper extends CalculationServiceTest.FakePredictionMapper {
        @Override
        public List<Long> findTypeIds(int taxYear, String typeCode, long totalSalary) {
            return List.of(1L, 2L);
        }
    }

    private CalculationServiceTest.FakePredictionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CalculationServiceTest.FakePredictionMapper();
    }

    private IncomeResponseDTO income() {
        IncomeResponseDTO income = new IncomeResponseDTO();
        income.setTaxYear(2026);
        income.setTotalSalary(36_000_000L);
        return income;
    }

    private CalculationResponseDTO result(Integer taxYear) {
        CalculationResponseDTO result = new CalculationResponseDTO();
        result.setTaxYear(taxYear);
        result.setDetails(
                List.of(
                        new DeductionDetailDTO(
                                "EARNED_DEDUCTION",
                                "근로소득공제",
                                "INCOME_DEDUCTION",
                                36_000_000L,
                                10_650_000L,
                                "신고안내"),
                        new DeductionDetailDTO(
                                "STANDARD_CREDIT", "표준세액공제", "TAX_CREDIT", 0L, 130_000L, "신고안내")));
        return result;
    }

    @Test
    void 결과와_입력값과_항목별_내역을_모두_저장하고_번호를_돌려준다() {
        long predictionId =
                new PredictionSaveService(mapper).save(81L, 2026, income(), "N", result(2026));

        assertEquals(101L, predictionId);
        assertEquals(1, mapper.predictionCount);
        assertEquals(1, mapper.inputCount);
        assertEquals(2, mapper.detailTypeIds.size());
    }

    @Test
    void 계산_결과_연도가_요청_연도와_다르면_저장하지_않는다() {
        PredictionSaveService service = new PredictionSaveService(mapper);
        assertThrows(
                IllegalStateException.class,
                () -> service.save(81L, 2026, income(), "N", result(2025)));
        assertEquals(0, mapper.predictionCount);
    }

    @Test
    void 계산_결과에_연도가_없으면_저장하지_않는다() {
        PredictionSaveService service = new PredictionSaveService(mapper);
        assertThrows(
                IllegalStateException.class,
                () -> service.save(81L, 2026, income(), "N", result(null)));
        assertEquals(0, mapper.predictionCount);
    }

    @Test
    void 항목별_내역이_없으면_저장하지_않는다() {
        CalculationResponseDTO noDetails = result(2026);
        noDetails.setDetails(null);
        PredictionSaveService service = new PredictionSaveService(mapper);
        assertThrows(
                IllegalStateException.class,
                () -> service.save(81L, 2026, income(), "N", noDetails));
        assertEquals(0, mapper.predictionCount);
    }

    @Test
    void 기준값이_하나로_정해지지_않으면_저장을_멈춘다() {
        // 실제 DB에서는 @Transactional로 앞서 넣은 PREDICTION까지 전부 취소돼요 (반쪽 결과 방지)
        DuplicateTypeMapper duplicate = new DuplicateTypeMapper();
        PredictionSaveService service = new PredictionSaveService(duplicate);
        assertThrows(
                IllegalStateException.class,
                () -> service.save(81L, 2026, income(), "N", result(2026)));
        assertEquals(0, duplicate.detailTypeIds.size());
    }
}
