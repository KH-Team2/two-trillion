package com.hoperefund.prediction.dto;

import java.time.LocalDateTime;

/** 결과 이력 목록 한 줄 (F-04-02) · 현재(기준 결과)와 시나리오가 함께 나와요. 항목별 내역은 하나를 열 때만 가져와요. */
public class PredictionSummaryDTO {

    private Long predictionId; // PREDICTION.PREDICTION_ID
    private Integer taxYear; // 귀속 연도
    private String scenarioName; // 「현재」 또는 시나리오 이름
    private Long basePredictionId; // 기준 결과 번호 (현재면 null)
    private Long expectedRefund; // 예상 환급액 (음수 = 추가 납부)
    private LocalDateTime predictedAt; // 계산일

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

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public Long getBasePredictionId() {
        return basePredictionId;
    }

    public void setBasePredictionId(Long basePredictionId) {
        this.basePredictionId = basePredictionId;
    }

    public Long getExpectedRefund() {
        return expectedRefund;
    }

    public void setExpectedRefund(Long expectedRefund) {
        this.expectedRefund = expectedRefund;
    }

    public LocalDateTime getPredictedAt() {
        return predictedAt;
    }

    public void setPredictedAt(LocalDateTime predictedAt) {
        this.predictedAt = predictedAt;
    }
}
