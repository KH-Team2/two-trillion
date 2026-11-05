package com.hoperefund.prediction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hoperefund.common.exception.NoCriteriaException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.mapper.IncomeMapper;
import com.hoperefund.income.service.IncomeService;
import com.hoperefund.prediction.client.CalcClient;
import com.hoperefund.prediction.client.MockCalcClient;
import com.hoperefund.prediction.dto.BenefitResponseDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.CardSpendDTO;
import com.hoperefund.prediction.dto.MemberQualificationDTO;
import com.hoperefund.prediction.mapper.PredictionMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DB 없이 계산 요청 · 저장 흐름만 확인해요 (가짜 매퍼 + 가짜 계산). */
class CalculationServiceTest {

    /** 메모리에만 저장하는 가짜 IncomeMapper (회원번호-연도로 찾아요) */
    static class FakeIncomeMapper implements IncomeMapper {
        final Map<String, IncomeResponseDTO> rows = new HashMap<>();

        @Override
        public IncomeResponseDTO findByMemberAndYear(long memberId, int taxYear) {
            return rows.get(memberId + "-" + taxYear);
        }

        @Override
        public int upsert(long memberId, int taxYear, IncomeRequestDTO req) {
            return 0;
        }

        /** 기준값은 2025 · 2026년만 있다고 봐요 (공용 DB와 같게) */
        @Override
        public int countCriteria(int taxYear) {
            return taxYear == 2025 || taxYear == 2026 ? 1 : 0;
        }
    }

    /** 저장한 내용을 기록만 하는 가짜 PredictionMapper */
    static class FakePredictionMapper implements PredictionMapper {
        final Map<Long, String> homeless = new HashMap<>();
        final Set<String> missingCodes = new HashSet<>();
        int predictionCount = 0;
        int inputCount = 0;
        String savedHomelessYn;
        final List<Long> detailTypeIds = new ArrayList<>();

        @Override
        public String findHomelessHeadYn(long memberId) {
            return homeless.get(memberId);
        }

        MemberQualificationDTO qualification; // 혜택 진단 테스트용

        @Override
        public MemberQualificationDTO findMemberQualification(long memberId) {
            return qualification;
        }

        @Override
        public List<CardSpendDTO> findCardSpends(long memberId, int taxYear) {
            return List.of(new CardSpendDTO(1L, "CREDIT", 12_000_000L));
        }

        @Override
        public List<Long> findTypeIds(int taxYear, String typeCode, long totalSalary) {
            return missingCodes.contains(typeCode)
                    ? List.of()
                    : List.of((long) typeCode.hashCode());
        }

        @Override
        public long nextPredictionId() {
            return 101L;
        }

        @Override
        public int insertPrediction(
                long predictionId, long memberId, CalculationResponseDTO result) {
            predictionCount++;
            return 1;
        }

        @Override
        public int insertPredictionInput(
                long predictionId, IncomeResponseDTO income, String homelessHeadYn) {
            inputCount++;
            savedHomelessYn = homelessHeadYn;
            return 1;
        }

        @Override
        public int insertPredictionDetail(
                long predictionId, long typeId, long baseAmount, long deductionAmount) {
            detailTypeIds.add(typeId);
            return 1;
        }
    }

    private FakeIncomeMapper incomeMapper;
    private FakePredictionMapper predictionMapper;

    @BeforeEach
    void setUp() {
        incomeMapper = new FakeIncomeMapper();
        predictionMapper = new FakePredictionMapper();
    }

    /** 저장 흐름을 보려고 mode는 "http"로 줘요 (계산은 가짜 MockCalcClient) */
    private CalculationService service(boolean saveEnabled) {
        return service(saveEnabled, "http");
    }

    private CalculationService service(boolean saveEnabled, String calcMode) {
        return service(new MockCalcClient(), saveEnabled, calcMode);
    }

    private CalculationService service(
            CalcClient calcClient, boolean saveEnabled, String calcMode) {
        return new CalculationService(
                new IncomeService(incomeMapper),
                calcClient,
                new PredictionSaveService(predictionMapper),
                predictionMapper,
                saveEnabled,
                calcMode);
    }

    /** 진단 결과(중소기업 감면)를 정해 두고, 계산에 넘긴 감면 신청 값을 기록하는 가짜 계산 */
    static class SmeCalcClient extends MockCalcClient {
        final Boolean smeEligible;
        String sentSmeReductionYn;

        SmeCalcClient(Boolean smeEligible) {
            this.smeEligible = smeEligible;
        }

        @Override
        public CalculationResponseDTO calculate(
                int taxYear,
                IncomeResponseDTO income,
                String homelessHeadYn,
                List<CardSpendDTO> cardSpends) {
            sentSmeReductionYn = income.getSmeReductionYn();
            return super.calculate(taxYear, income, homelessHeadYn, cardSpends);
        }

        @Override
        public BenefitResponseDTO diagnose(
                int taxYear, long totalSalary, MemberQualificationDTO member) {
            BenefitResponseDTO response = super.diagnose(taxYear, totalSalary, member);
            response.getResults()
                    .forEach(
                            r -> {
                                if ("SME_YOUTH".equals(r.getBenefitCode())) {
                                    r.setEligible(smeEligible);
                                }
                            });
            return response;
        }
    }

    private SmeCalcClient calculateWithSme(Boolean smeEligible) {
        saveIncome(1L, 2026);
        incomeMapper.rows.get("1-2026").setSmeReductionYn("Y");
        predictionMapper.homeless.put(1L, "N");
        predictionMapper.qualification = new MemberQualificationDTO();
        SmeCalcClient client = new SmeCalcClient(smeEligible);
        service(client, false, "http").calculate(1L, 2026);
        return client;
    }

    private void saveIncome(long memberId, int taxYear) {
        IncomeResponseDTO income = new IncomeResponseDTO();
        income.setTaxYear(taxYear);
        income.setTotalSalary(36_000_000L);
        income.setWithheldTax(1_523_000L);
        income.setNationalPension(1_620_000L);
        income.setHealthInsurance(1_276_000L);
        income.setPensionSaving(6_000_000L);
        income.setIrpSaving(3_000_000L);
        incomeMapper.rows.put(memberId + "-" + taxYear, income);
    }

    @Test
    void 소득이_있으면_기납부세액이_결과에_들어간다() {
        // Given
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "N");
        // When
        CalculationResponseDTO result = service(false).calculate(1L, 2026);
        // Then
        assertEquals(1_523_000L, result.getWithheldTax().longValue());
    }

    @Test
    void 카드_공제는_6개_행으로_나온다() {
        // Given
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "N");
        // When
        CalculationResponseDTO result = service(false).calculate(1L, 2026);
        // Then
        assertEquals(
                6, result.getDetails().stream().filter(d -> "CARD".equals(d.getKind())).count());
    }

    @Test
    void 다른_회원의_소득으로는_계산하지_않는다() {
        // Given: 1번 회원 소득만 있음
        saveIncome(1L, 2026);
        // When · Then: 2번 회원이 요청하면 1번 소득을 읽지 않고 없음 오류
        assertThrows(NotFoundException.class, () -> service(false).calculate(2L, 2026));
    }

    @Test
    void 기준값이_없는_연도는_계산하지_않는다() {
        // When · Then
        assertThrows(NoCriteriaException.class, () -> service(false).calculate(1L, 2024));
    }

    @Test
    void 회원_정보가_없으면_계산하지_않는다() {
        // Given: 소득은 있는데 회원 정보(무주택 여부)가 없음
        saveIncome(1L, 2026);
        // When · Then
        assertThrows(NotFoundException.class, () -> service(false).calculate(1L, 2026));
    }

    @Test
    void 저장이_꺼져_있으면_DB에_저장하지_않는다() {
        // Given
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "N");
        // When
        CalculationResponseDTO result = service(false).calculate(1L, 2026);
        // Then
        assertNull(result.getPredictionId());
        assertEquals(0, predictionMapper.predictionCount);
    }

    @Test
    void 저장이_켜져_있으면_결과_입력값_상세를_모두_저장한다() {
        // Given
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "N");
        // When
        CalculationResponseDTO result = service(true).calculate(1L, 2026);
        // Then: 결과 1 · 입력값 1 · 상세는 결과 항목 수만큼 · 결과 번호가 응답에 들어감
        assertEquals(101L, result.getPredictionId().longValue());
        assertEquals(1, predictionMapper.predictionCount);
        assertEquals(1, predictionMapper.inputCount);
        assertEquals(result.getDetails().size(), predictionMapper.detailTypeIds.size());
    }

    @Test
    void 무주택_세대주_여부는_회원_정보에서_복사한다() {
        // Given
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "Y");
        // When
        service(true).calculate(1L, 2026);
        // Then
        assertEquals("Y", predictionMapper.savedHomelessYn);
    }

    @Test
    void 기준값_항목을_찾지_못하면_저장을_멈춘다() {
        // Given: 결과에 있는 코드 하나가 DB 기준값에 없음
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "N");
        predictionMapper.missingCodes.add("CARD_CHECK");
        // When · Then (실제 DB에서는 @Transactional로 앞에서 넣은 행도 모두 취소돼요)
        assertThrows(IllegalStateException.class, () -> service(true).calculate(1L, 2026));
    }

    @Test
    void 가짜_계산_모드에서는_저장을_켜도_저장하지_않는다() {
        // Given
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "N");
        // When: SAVE_ENABLED=true · CALC_MODE=mock
        CalculationResponseDTO result = service(true, "mock").calculate(1L, 2026);
        // Then
        assertNull(result.getPredictionId());
        assertEquals(0, predictionMapper.predictionCount);
    }

    @Test
    void 감면을_신청했고_진단이_해당이면_감면을_넣어_계산한다() {
        assertEquals("Y", calculateWithSme(true).sentSmeReductionYn);
    }

    @Test
    void 감면을_신청해도_진단이_비해당이면_감면_없이_계산한다() {
        assertEquals("N", calculateWithSme(false).sentSmeReductionYn);
    }

    @Test
    void 진단_결과가_비어_있으면_감면_없이_계산한다() {
        // Given: 계산 서버가 진단 결과 목록 없이 응답
        saveIncome(1L, 2026);
        incomeMapper.rows.get("1-2026").setSmeReductionYn("Y");
        predictionMapper.homeless.put(1L, "N");
        predictionMapper.qualification = new MemberQualificationDTO();
        SmeCalcClient client =
                new SmeCalcClient(true) {
                    @Override
                    public BenefitResponseDTO diagnose(
                            int taxYear, long totalSalary, MemberQualificationDTO member) {
                        BenefitResponseDTO empty = new BenefitResponseDTO();
                        empty.setResults(null);
                        return empty;
                    }
                };
        // When
        service(client, false, "http").calculate(1L, 2026);
        // Then
        assertEquals("N", client.sentSmeReductionYn);
    }

    @Test
    void 감면을_신청해도_정보가_부족하면_감면_없이_계산한다() {
        assertEquals("N", calculateWithSme(null).sentSmeReductionYn);
    }
}
