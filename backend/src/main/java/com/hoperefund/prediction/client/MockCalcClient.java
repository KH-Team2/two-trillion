package com.hoperefund.prediction.client;

import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.prediction.dto.BenefitResponseDTO;
import com.hoperefund.prediction.dto.BenefitResultDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.CardSpendDTO;
import com.hoperefund.prediction.dto.DeductionDetailDTO;
import com.hoperefund.prediction.dto.MemberQualificationDTO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 가짜 계산 결과 (파이썬 서버 연결 전까지 화면 연결용) - 세액을 실제로 계산하지 않아요. 과세표준·산출세액·결정세액·공제액은 모두 0이에요. - 대상
 * 금액(baseAmount)에는 입력한 소득 값만 그대로 넣어요. - 항목 코드·이름·종류·근거는 DB DEDUCTION_TYPE(2026) 기준이에요.
 *
 * <p>calc.mode가 mock이거나 비어 있을 때만 Bean으로 등록돼요 (http면 HttpCalcClient가 쓰여요).
 *
 * <p>운영에서 CALC_MODE=http를 빠뜨려도 가짜 결과가 진짜처럼 보이지 않게: 서버가 켜질 때 경고 로그를 남기고, 항목별 근거(sourceNote) 앞에 「[가짜
 * 계산 · 실제 세액 아님]」을 붙여 화면에도 보이게 해요.
 */
@Component
@ConditionalOnProperty(name = "calc.mode", havingValue = "mock", matchIfMissing = true)
public class MockCalcClient implements CalcClient {

    private static final Logger log = LoggerFactory.getLogger(MockCalcClient.class);

    /** 화면 항목별 근거 앞에 붙는 표시 (가짜 결과가 진짜처럼 보이지 않게) */
    static final String MOCK_NOTE = "[가짜 계산 · 실제 세액 아님] ";

    public MockCalcClient() {
        log.warn(
                "CALC_MODE=mock: 가짜 계산으로 응답해요 (결정세액 0 · 기납부세액 전부가 환급으로 보임)."
                        + " 실제 서비스에서는 CALC_MODE=http와 계산 서버 주소·토큰을 설정하세요");
    }

    private static final String CARD_SOURCE = "국세청 신고안내 132p";

    @Override
    public CalculationResponseDTO calculate(
            int taxYear,
            IncomeResponseDTO income,
            String homelessHeadYn,
            List<CardSpendDTO> cardSpends) {
        CalculationResponseDTO result = new CalculationResponseDTO();
        result.setTaxYear(taxYear);
        result.setTotalSalary(income.getTotalSalary());
        result.setWithheldTax(income.getWithheldTax());
        result.setTaxableIncome(0L);
        result.setCalculatedTax(0L);
        result.setDecidedTax(0L);
        // 환급액 = 기납부세액 - 결정세액 (가짜라 결정세액이 0 → 기납부세액 전부가 환급으로 보여요)
        result.setExpectedRefund(income.getWithheldTax() - result.getDecidedTax());

        // 연금계좌 대상 금액 = 연금저축 + IRP (DB는 두 칸 · v4.1)
        long pensionTotal = income.getPensionSaving() + income.getIrpSaving();

        result.setDetails(
                List.of(
                        new DeductionDetailDTO(
                                "EARNED_DEDUCTION",
                                "근로소득공제",
                                "INCOME_DEDUCTION",
                                income.getTotalSalary(),
                                0L,
                                "국세청 신고안내 94p"),
                        new DeductionDetailDTO(
                                "NATIONAL_PENSION",
                                "국민연금보험료 공제",
                                "INCOME_DEDUCTION",
                                income.getNationalPension(),
                                0L,
                                "국세청 신고안내 103p"),
                        new DeductionDetailDTO(
                                "HEALTH_INSURANCE",
                                "건강·고용보험료 공제",
                                "INCOME_DEDUCTION",
                                income.getHealthInsurance(),
                                0L,
                                "국세청 신고안내 107p"),
                        // 카드 공제 6개 행 (카드 내역 연결 전이라 대상 금액 0)
                        new DeductionDetailDTO(
                                "CARD_CREDIT", "신용카드(일반)", "CARD", 0L, 0L, CARD_SOURCE),
                        new DeductionDetailDTO(
                                "CARD_CHECK", "체크카드(일반)", "CARD", 0L, 0L, CARD_SOURCE),
                        new DeductionDetailDTO(
                                "CARD_CASH", "현금영수증(일반)", "CARD", 0L, 0L, CARD_SOURCE),
                        new DeductionDetailDTO("CARD_TRANSIT", "대중교통", "CARD", 0L, 0L, CARD_SOURCE),
                        new DeductionDetailDTO("CARD_MARKET", "전통시장", "CARD", 0L, 0L, CARD_SOURCE),
                        new DeductionDetailDTO("CARD_CULTURE", "문화체육", "CARD", 0L, 0L, CARD_SOURCE),
                        new DeductionDetailDTO(
                                "EARNED_CREDIT", "근로소득세액공제", "TAX_CREDIT", 0L, 0L, "국세청 신고안내 162p"),
                        new DeductionDetailDTO(
                                "PENSION", "연금계좌", "TAX_CREDIT", pensionTotal, 0L, "국세청 신고안내 요약표"),
                        new DeductionDetailDTO(
                                "STANDARD_CREDIT",
                                "표준세액공제(근로자)",
                                "TAX_CREDIT",
                                0L,
                                0L,
                                "국세청 신고안내 170p")));
        // 모든 항목 근거 앞에 가짜 표시 (화면 「공제 항목별 내역」의 근거 칸에 보여요)
        result.getDetails().forEach(d -> d.setSourceNote(MOCK_NOTE + d.getSourceNote()));
        log.warn("가짜 계산 결과를 돌려줬어요 (CALC_MODE=mock) year={}", taxYear);
        return result;
    }

    /** 가짜 진단: 판정하지 않고 「계산 서버 연결 전」 안내만 돌려줘요. */
    @Override
    public BenefitResponseDTO diagnose(
            int taxYear, long totalSalary, MemberQualificationDTO member) {
        BenefitResponseDTO response = new BenefitResponseDTO();
        response.setTaxYear(taxYear);
        for (String[] benefit :
                new String[][] {
                    {"SME_YOUTH", "중소기업 취업자 소득세 감면"},
                    {"RENT", "월세 세액공제"},
                    {"HOUSING_SAVING", "주택청약종합저축 소득공제"}
                }) {
            BenefitResultDTO result = new BenefitResultDTO();
            result.setBenefitCode(benefit[0]);
            result.setBenefitName(benefit[1]);
            result.setEligible(null);
            result.setReasons(List.of("계산 서버 연결 전이라 아직 진단하지 않았어요"));
            response.getResults().add(result);
        }
        return response;
    }
}
