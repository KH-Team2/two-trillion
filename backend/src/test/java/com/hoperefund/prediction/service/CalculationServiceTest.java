package com.hoperefund.prediction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hoperefund.common.exception.BadRequestException;
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
import com.hoperefund.prediction.dto.DeductionDetailDTO;
import com.hoperefund.prediction.dto.MemberQualificationDTO;
import com.hoperefund.prediction.dto.SimulationRequestDTO;
import com.hoperefund.prediction.mapper.PredictionMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

        CalculationResponseDTO latest; // 조회 테스트용: 저장돼 있다고 칠 결과 (null이면 없음)
        List<DeductionDetailDTO> latestDetails = List.of(); // 조회 테스트용: 그 결과의 내역

        @Override
        public CalculationResponseDTO findLatestPrediction(long memberId, int taxYear) {
            return latest;
        }

        @Override
        public List<DeductionDetailDTO> findPredictionDetails(long memberId, long predictionId) {
            return latestDetails;
        }

        final Set<Long> ownPredictionIds = new HashSet<>(); // 시뮬레이션 테스트용: 본인 결과라고 칠 번호

        @Override
        public int countOwnPrediction(long predictionId, long memberId, int taxYear) {
            return ownPredictionIds.contains(predictionId) ? 1 : 0;
        }

        String savedScenarioName; // 시뮬레이션 테스트용: 저장한 시나리오 이름
        Long savedBasePredictionId; // 시뮬레이션 테스트용: 저장한 기준 결과 번호

        @Override
        public int insertScenarioPrediction(
                long predictionId,
                long memberId,
                CalculationResponseDTO result,
                String scenarioName,
                long basePredictionId) {
            predictionCount++;
            savedScenarioName = scenarioName;
            savedBasePredictionId = basePredictionId;
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
    @DisplayName("소득이 있으면 기납부세액이 결과에 들어간다")
    void includesWithheldTaxInResult() {
        // Given
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "N");
        // When
        CalculationResponseDTO result = service(false).calculate(1L, 2026);
        // Then
        assertEquals(1_523_000L, result.getWithheldTax().longValue());
    }

    @Test
    @DisplayName("카드 공제는 6개 행으로 나온다")
    void returnsSixCardDeductionRows() {
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
    @DisplayName("다른 회원의 소득으로는 계산하지 않는다")
    void doesNotCalculateWithOtherMembersIncome() {
        // Given: 1번 회원 소득만 있음
        saveIncome(1L, 2026);
        // When · Then: 2번 회원이 요청하면 1번 소득을 읽지 않고 없음 오류
        assertThrows(NotFoundException.class, () -> service(false).calculate(2L, 2026));
    }

    @Test
    @DisplayName("기준값이 없는 연도는 계산하지 않는다")
    void doesNotCalculateYearWithoutCriteria() {
        // When · Then
        assertThrows(NoCriteriaException.class, () -> service(false).calculate(1L, 2024));
    }

    @Test
    @DisplayName("회원 정보가 없으면 계산하지 않는다")
    void doesNotCalculateWithoutMember() {
        // Given: 소득은 있는데 회원 정보(무주택 여부)가 없음
        saveIncome(1L, 2026);
        // When · Then
        assertThrows(NotFoundException.class, () -> service(false).calculate(1L, 2026));
    }

    @Test
    @DisplayName("저장이 꺼져 있으면 DB에 저장하지 않는다")
    void doesNotSaveWhenSaveDisabled() {
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
    @DisplayName("저장이 켜져 있으면 결과 입력값 상세를 모두 저장한다")
    void savesResultInputAndDetailsWhenSaveEnabled() {
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
    @DisplayName("무주택 세대주 여부는 회원 정보에서 복사한다")
    void copiesHomelessHeadFromMember() {
        // Given
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "Y");
        // When
        service(true).calculate(1L, 2026);
        // Then
        assertEquals("Y", predictionMapper.savedHomelessYn);
    }

    @Test
    @DisplayName("기준값 항목을 찾지 못하면 저장을 멈춘다")
    void stopsSavingWhenCriteriaTypeNotFound() {
        // Given: 결과에 있는 코드 하나가 DB 기준값에 없음
        saveIncome(1L, 2026);
        predictionMapper.homeless.put(1L, "N");
        predictionMapper.missingCodes.add("CARD_CHECK");
        // When · Then (실제 DB에서는 @Transactional로 앞에서 넣은 행도 모두 취소돼요)
        assertThrows(IllegalStateException.class, () -> service(true).calculate(1L, 2026));
    }

    @Test
    @DisplayName("가짜 계산 모드에서는 저장을 켜도 저장하지 않는다")
    void doesNotSaveInMockModeEvenIfEnabled() {
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
    @DisplayName("감면을 신청했고 진단이 해당이면 감면을 넣어 계산한다")
    void appliesReductionWhenAppliedAndEligible() {
        assertEquals("Y", calculateWithSme(true).sentSmeReductionYn);
    }

    @Test
    @DisplayName("감면을 신청해도 진단이 비해당이면 감면 없이 계산한다")
    void skipsReductionWhenNotEligible() {
        assertEquals("N", calculateWithSme(false).sentSmeReductionYn);
    }

    @Test
    @DisplayName("진단 결과가 비어 있으면 감면 없이 계산한다")
    void skipsReductionWhenDiagnosisEmpty() {
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
    @DisplayName("감면을 신청해도 정보가 부족하면 감면 없이 계산한다")
    void skipsReductionWhenInfoInsufficient() {
        assertEquals("N", calculateWithSme(null).sentSmeReductionYn);
    }

    @Test
    @DisplayName("저장된 계산 결과가 없으면 없음 오류를 낸다")
    void throwsNotFoundWhenNoSavedResult() {
        // Given: 저장된 결과가 없음 (가짜 매퍼의 latest가 null)
        // When · Then
        assertThrows(NotFoundException.class, () -> service(false).getLatest(81L, 2026));
    }

    @Test
    @DisplayName("저장된 계산 결과가 있으면 내역을 붙여서 돌려준다")
    void returnsLatestResultWithDetails() {
        // Given: 저장된 결과 1건과 내역 2줄이 있다고 침
        CalculationResponseDTO saved = new CalculationResponseDTO();
        saved.setPredictionId(3L);
        saved.setExpectedRefund(51_500L);
        predictionMapper.latest = saved;
        predictionMapper.latestDetails =
                List.of(new DeductionDetailDTO(), new DeductionDetailDTO());
        // When
        CalculationResponseDTO result = service(false).getLatest(81L, 2026);
        // Then
        assertEquals(51_500L, result.getExpectedRefund().longValue());
        assertEquals(2, result.getDetails().size());
    }

    // ───────── 조건 변경 시뮬레이션 (F-04-01) ─────────

    /** 시뮬레이션 요청: 기준 결과 3번 · 총급여 4,000만 · 월세 50만 · 카드 신용 1,000만 */
    private SimulationRequestDTO simulationRequest() {
        IncomeRequestDTO income = new IncomeRequestDTO();
        income.setTotalSalary(40_000_000L);
        income.setWithheldTax(2_000_000L);
        income.setMonthlyRent(500_000L);
        SimulationRequestDTO request = new SimulationRequestDTO();
        request.setBasePredictionId(3L);
        request.setScenarioName("월세 늘리면");
        request.setIncome(income);
        request.setHomelessHeadYn("Y");
        request.setCardSpends(List.of(new CardSpendDTO(1L, "CREDIT", 10_000_000L)));
        return request;
    }

    @Test
    @DisplayName("시뮬레이션: 기준 결과가 본인 것이 아니면 없음 오류를 낸다")
    void simulateRejectsOtherMembersBase() {
        // Given: 3번 결과를 본인 결과로 등록하지 않음 (남의 결과이거나 다른 연도)
        // When · Then
        assertThrows(
                NotFoundException.class,
                () -> service(true).simulate(81L, 2026, simulationRequest()));
        assertEquals(0, predictionMapper.predictionCount);
    }

    @Test
    @DisplayName("시뮬레이션: 저장이 켜져 있으면 시나리오 이름과 기준 결과 번호로 저장한다")
    void simulateSavesAsScenario() {
        // Given
        predictionMapper.ownPredictionIds.add(3L);
        // When
        CalculationResponseDTO result = service(true).simulate(81L, 2026, simulationRequest());
        // Then: 시나리오용 INSERT로 저장 · 입력값 · 상세도 함께
        assertEquals(101L, result.getPredictionId().longValue());
        assertEquals("월세 늘리면", predictionMapper.savedScenarioName);
        assertEquals(3L, predictionMapper.savedBasePredictionId.longValue());
        assertEquals(1, predictionMapper.inputCount);
        assertEquals("Y", predictionMapper.savedHomelessYn);
        assertEquals(result.getDetails().size(), predictionMapper.detailTypeIds.size());
    }

    @Test
    @DisplayName("시뮬레이션: 저장된 소득(INCOME)이 아니라 요청 값으로 계산한다")
    void simulateUsesRequestValuesNotIncome() {
        // Given: INCOME에는 총급여 3,600만이 있음
        saveIncome(81L, 2026);
        predictionMapper.ownPredictionIds.add(3L);
        // When: 요청은 총급여 4,000만
        CalculationResponseDTO result = service(false).simulate(81L, 2026, simulationRequest());
        // Then: 요청 값으로 계산 · INCOME은 그대로
        assertEquals(40_000_000L, result.getTotalSalary().longValue());
        assertEquals(36_000_000L, incomeMapper.rows.get("81-2026").getTotalSalary().longValue());
        assertNull(result.getPredictionId());
    }

    @Test
    @DisplayName("시뮬레이션: 자녀 수가 부양가족 수보다 많으면 계산하지 않는다")
    void simulateRejectsMoreChildrenThanDependents() {
        // Given
        predictionMapper.ownPredictionIds.add(3L);
        SimulationRequestDTO request = simulationRequest();
        request.getIncome().setDependents(1);
        request.getIncome().setChildren(2);
        // When · Then
        assertThrows(BadRequestException.class, () -> service(true).simulate(81L, 2026, request));
        assertEquals(0, predictionMapper.predictionCount);
    }
}
