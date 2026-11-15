package com.hoperefund.prediction.service;

import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.service.IncomeService;
import com.hoperefund.prediction.client.CalcClient;
import com.hoperefund.prediction.dto.BenefitResponseDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.CardSpendDTO;
import com.hoperefund.prediction.dto.MemberQualificationDTO;
import com.hoperefund.prediction.dto.SimulationRequestDTO;
import com.hoperefund.prediction.mapper.PredictionMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계산 요청 (F-03-01) 흐름: Controller → CalculationService → (소득 · 무주택 · 카드 합계 조회) → CalcClient(계산) →
 * PredictionSaveService(저장) → 결과 반환
 *
 * <p>계산 서버 호출은 트랜잭션 밖에서 해요. 저장할 때만 PredictionSaveService가 트랜잭션을 열어요. 저장은 prediction.save-enabled가
 * true이고 calc.mode가 http일 때만 해요. mock(가짜 계산)일 때는 저장을 켜 두어도 저장하지 않아요 → 가짜 결과(세액 0)가 공용 DB에 쌓이지 않게.
 *
 * <p>중소기업 취업자 감면은 회원이 「신청(Y)」했고, 혜택 진단(F-03-02)이 「해당」일 때만 계산에 넣어요. 진단이 「비해당」이거나 「정보 부족(입사일·회사 규모
 * 없음)」이면 감면 없이(N) 계산해요 (기능명세서 F-03-02 ④).
 */
@Service
public class CalculationService {

    private static final Logger log = LoggerFactory.getLogger(CalculationService.class);
    private static final String SME_BENEFIT_CODE = "SME_YOUTH";

    private final IncomeService incomeService;
    private final CalcClient calcClient;
    private final PredictionSaveService predictionSaveService;
    private final PredictionMapper predictionMapper;
    private final boolean saveEnabled;

    // 생성자 주입: @Value는 application.yml의 설정 값을 읽어 넣어 줘요
    public CalculationService(
            IncomeService incomeService,
            CalcClient calcClient,
            PredictionSaveService predictionSaveService,
            PredictionMapper predictionMapper,
            @Value("${prediction.save-enabled:false}") boolean saveEnabled,
            @Value("${calc.mode:mock}") String calcMode) {
        this.incomeService = incomeService;
        this.calcClient = calcClient;
        this.predictionSaveService = predictionSaveService;
        this.predictionMapper = predictionMapper;
        boolean realCalc = "http".equals(calcMode);
        if (saveEnabled && !realCalc) {
            log.warn("CALC_MODE가 http가 아니라서(현재 {}) SAVE_ENABLED=true여도 계산 결과를 저장하지 않아요", calcMode);
        }
        this.saveEnabled = saveEnabled && realCalc;
    }

    public CalculationResponseDTO calculate(long memberId, int taxYear) {
        long started = System.currentTimeMillis();
        log.info("계산 요청 member={} year={}", memberId, taxYear); // 금액은 남기지 않아요

        // 1. 본인(memberId) 데이터만 읽어요 · 연도 검사 · 소득 없음 오류는 IncomeService 규칙 그대로
        IncomeResponseDTO income = incomeService.get(memberId, taxYear);
        String homelessHeadYn = predictionMapper.findHomelessHeadYn(memberId);
        if (homelessHeadYn == null) {
            throw new NotFoundException("회원 정보를 찾을 수 없어요");
        }
        List<CardSpendDTO> cardSpends = predictionMapper.findCardSpends(memberId, taxYear);

        // 감면 신청(Y)이어도 진단이 「해당」이 아니면 감면 없이 계산 · 저장되는 입력값(PREDICTION_INPUT)도 실제로 쓴 값(N)
        if ("Y".equals(income.getSmeReductionYn())
                && !isSmeEligible(memberId, taxYear, income.getTotalSalary())) {
            log.info("감면 신청(Y)이지만 진단이 「해당」이 아니라 감면 없이 계산 member={} year={}", memberId, taxYear);
            income.setSmeReductionYn("N");
        }

        // 2. 계산은 CalcClient에 맡겨요 (mock 또는 파이썬 서버) · 트랜잭션 밖
        CalculationResponseDTO result =
                calcClient.calculate(taxYear, income, homelessHeadYn, cardSpends);

        // 3. 저장 (설정이 켜져 있고 실제 계산일 때만) · 여기서만 트랜잭션
        if (saveEnabled) {
            long predictionId =
                    predictionSaveService.save(memberId, taxYear, income, homelessHeadYn, result);
            result.setPredictionId(predictionId);
        }
        log.info(
                "계산 완료 member={} year={} predictionId={} {}ms",
                memberId,
                taxYear,
                result.getPredictionId(),
                System.currentTimeMillis() - started);
        return result;
    }

    /**
     * 조건 변경 시뮬레이션 (F-04-01) · 요청으로 받은 값으로 다시 계산하고 시나리오로 저장해요.
     *
     * <p>INCOME · EXPENSE는 읽지도 고치지도 않아요. 기준 결과는 본인의 같은 연도 결과여야 해요 (남의 결과 번호로 연결하는 것 방지).
     */
    public CalculationResponseDTO simulate(
            long memberId, int taxYear, SimulationRequestDTO request) {
        long started = System.currentTimeMillis();
        log.info("시뮬레이션 요청 member={} year={}", memberId, taxYear); // 금액은 남기지 않아요

        // 0. 기준 결과가 본인의 그 연도 결과인지 (아니면 없는 것처럼 404 · 남의 결과가 있는지도 알려 주지 않음)
        long basePredictionId = request.getBasePredictionId();
        if (predictionMapper.countOwnPrediction(basePredictionId, memberId, taxYear) != 1) {
            throw new NotFoundException("기준 결과를 찾을 수 없어요");
        }

        // 1. 요청 값을 계산용 소득으로 옮겨요 (자녀 수 ≤ 부양가족 수는 소득 입력과 같은 규칙)
        IncomeResponseDTO income = toIncome(taxYear, request.getIncome());
        if (income.getChildren() > income.getDependents()) {
            throw new BadRequestException("20세 이하 자녀 수는 부양가족 수보다 많을 수 없어요");
        }

        // 2. 감면 신청(Y)이어도 진단이 「해당」이 아니면 감면 없이 (계산과 같은 규칙)
        if ("Y".equals(income.getSmeReductionYn())
                && !isSmeEligible(memberId, taxYear, income.getTotalSalary())) {
            income.setSmeReductionYn("N");
        }

        // 3. 계산 (트랜잭션 밖)
        CalculationResponseDTO result =
                calcClient.calculate(
                        taxYear, income, request.getHomelessHeadYn(), request.getCardSpends());

        // 4. 시나리오로 저장 (설정이 켜져 있고 실제 계산일 때만)
        if (saveEnabled) {
            long predictionId =
                    predictionSaveService.saveScenario(
                            memberId,
                            taxYear,
                            income,
                            request.getHomelessHeadYn(),
                            result,
                            request.getScenarioName(),
                            basePredictionId);
            result.setPredictionId(predictionId);
        }
        log.info(
                "시뮬레이션 완료 member={} year={} predictionId={} {}ms",
                memberId,
                taxYear,
                result.getPredictionId(),
                System.currentTimeMillis() - started);
        return result;
    }

    /** 시뮬레이션 요청의 소득 칸을 계산 · 저장에 쓰는 모양(IncomeResponseDTO)으로 옮겨요 */
    private IncomeResponseDTO toIncome(int taxYear, IncomeRequestDTO req) {
        IncomeResponseDTO income = new IncomeResponseDTO();
        income.setTaxYear(taxYear);
        income.setTotalSalary(req.getTotalSalary());
        income.setWithheldTax(req.getWithheldTax());
        income.setNationalPension(req.getNationalPension());
        income.setHealthInsurance(req.getHealthInsurance());
        income.setDependents(req.getDependents());
        income.setChildren(req.getChildren());
        income.setMonthlyRent(req.getMonthlyRent());
        income.setHousingSaving(req.getHousingSaving());
        income.setPensionSaving(req.getPensionSaving());
        income.setIrpSaving(req.getIrpSaving());
        income.setSmeReductionYn(req.getSmeReductionYn());
        return income;
    }

    /** 저장된 계산 결과 중 가장 최근 1건 조회 (대시보드 · F-06-01) · 본인 결과만 · 없으면 404 */
    @Transactional(readOnly = true)
    public CalculationResponseDTO getLatest(long memberId, int taxYear) {
        CalculationResponseDTO result = predictionMapper.findLatestPrediction(memberId, taxYear);
        if (result == null) {
            throw new NotFoundException(taxYear + "년 계산 결과가 아직 없어요");
        }
        result.setDetails(
                predictionMapper.findPredictionDetails(memberId, result.getPredictionId()));
        return result;
    }

    /** 중소기업 취업자 감면 진단 결과가 「해당(true)」인지. 비해당(false) · 정보 부족(null) · 회원 정보 없음은 모두 false. */
    private boolean isSmeEligible(long memberId, int taxYear, long totalSalary) {
        MemberQualificationDTO member = predictionMapper.findMemberQualification(memberId);
        if (member == null) {
            return false;
        }
        BenefitResponseDTO diagnosis = calcClient.diagnose(taxYear, totalSalary, member);
        if (diagnosis == null || diagnosis.getResults() == null) {
            // 진단 결과가 비면 「해당」으로 볼 근거가 없으니 감면 없이 (안전한 쪽)
            log.warn("혜택 진단 결과가 비어 있어 감면 없이 계산 member={} year={}", memberId, taxYear);
            return false;
        }
        return diagnosis.getResults().stream()
                .anyMatch(
                        r ->
                                SME_BENEFIT_CODE.equals(r.getBenefitCode())
                                        && Boolean.TRUE.equals(r.getEligible()));
    }
}
