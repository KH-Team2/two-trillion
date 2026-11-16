package com.hoperefund.prediction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.DeductionDetailDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("결과와 입력값과 항목별 내역을 모두 저장하고 번호를 돌려준다")
    void savesAllAndReturnsId() {
        long predictionId =
                new PredictionSaveService(mapper).save(81L, 2026, income(), "N", result(2026));

        assertEquals(101L, predictionId);
        assertEquals(1, mapper.predictionCount);
        assertEquals(1, mapper.inputCount);
        assertEquals(2, mapper.detailTypeIds.size());
    }

    @Test
    @DisplayName("계산 결과 연도가 요청 연도와 다르면 저장하지 않는다")
    void rejectsWhenResultYearDiffers() {
        PredictionSaveService service = new PredictionSaveService(mapper);
        assertThrows(
                IllegalStateException.class,
                () -> service.save(81L, 2026, income(), "N", result(2025)));
        assertEquals(0, mapper.predictionCount);
    }

    @Test
    @DisplayName("계산 결과에 연도가 없으면 저장하지 않는다")
    void rejectsWhenResultYearMissing() {
        PredictionSaveService service = new PredictionSaveService(mapper);
        assertThrows(
                IllegalStateException.class,
                () -> service.save(81L, 2026, income(), "N", result(null)));
        assertEquals(0, mapper.predictionCount);
    }

    @Test
    @DisplayName("항목별 내역이 없으면 저장하지 않는다")
    void rejectsWhenDetailsMissing() {
        CalculationResponseDTO noDetails = result(2026);
        noDetails.setDetails(null);
        PredictionSaveService service = new PredictionSaveService(mapper);
        assertThrows(
                IllegalStateException.class,
                () -> service.save(81L, 2026, income(), "N", noDetails));
        assertEquals(0, mapper.predictionCount);
    }

    @Test
    @DisplayName("기준값이 하나로 정해지지 않으면 저장을 멈춘다")
    void stopsWhenCriteriaTypeAmbiguous() {
        // 실제 DB에서는 @Transactional로 앞서 넣은 PREDICTION까지 전부 취소돼요 (반쪽 결과 방지)
        DuplicateTypeMapper duplicate = new DuplicateTypeMapper();
        PredictionSaveService service = new PredictionSaveService(duplicate);
        assertThrows(
                IllegalStateException.class,
                () -> service.save(81L, 2026, income(), "N", result(2026)));
        assertEquals(0, duplicate.detailTypeIds.size());
    }
}
