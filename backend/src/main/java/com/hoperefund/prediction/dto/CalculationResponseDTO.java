package com.hoperefund.prediction.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 계산 결과 (F-03-01 응답) - 칸 이름은 노션 「동석님 전달」 이름 그대로예요. - expectedRefund가 음수면 추가 납부예요. - predictionId는
 * 저장된 결과 번호예요 (지난 결과 다시 열기용 · 저장을 안 했으면 null).
 */
public class CalculationResponseDTO {

    private Long predictionId; // 저장된 결과 번호 (PREDICTION.PREDICTION_ID)
    private Integer taxYear; // 귀속 연도
    private Long totalSalary; // 총급여
    private Long withheldTax; // 기납부세액
    private Long taxableIncome; // 과세표준
    private Long calculatedTax; // 산출세액
    private Long decidedTax; // 결정세액
    private Long expectedRefund; // 환급액 (= 기납부세액 - 결정세액)
    private List<DeductionDetailDTO> details = new ArrayList<>(); // 항목별 공제 내역

    public Long getPredictionId() {
        return predictionId;
    }

    public void setPredictionId(Long predictionId) {
        this.predictionId = predictionId;
    }

    public Integer getTaxYear() {
        return taxYear;
    }

    public void setTaxYear(Integer taxYear) {
        this.taxYear = taxYear;
    }

    public Long getTotalSalary() {
        return totalSalary;
    }

    public void setTotalSalary(Long totalSalary) {
        this.totalSalary = totalSalary;
    }

    public Long getWithheldTax() {
        return withheldTax;
    }

    public void setWithheldTax(Long withheldTax) {
        this.withheldTax = withheldTax;
    }

    public Long getTaxableIncome() {
        return taxableIncome;
    }

    public void setTaxableIncome(Long taxableIncome) {
        this.taxableIncome = taxableIncome;
    }

    public Long getCalculatedTax() {
        return calculatedTax;
    }

    public void setCalculatedTax(Long calculatedTax) {
        this.calculatedTax = calculatedTax;
    }

    public Long getDecidedTax() {
        return decidedTax;
    }

    public void setDecidedTax(Long decidedTax) {
        this.decidedTax = decidedTax;
    }

    public Long getExpectedRefund() {
        return expectedRefund;
    }

    public void setExpectedRefund(Long expectedRefund) {
        this.expectedRefund = expectedRefund;
    }

    public List<DeductionDetailDTO> getDetails() {
        return details;
    }

    public void setDetails(List<DeductionDetailDTO> details) {
        this.details = details;
    }
}
