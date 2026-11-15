package com.hoperefund.prediction.dto;

import com.hoperefund.income.dto.IncomeRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/** 조건 변경 시뮬레이션 요청 (F-04-01) · 바꾼 값만이 아니라 입력값 전부를 받아요. INCOME은 덮어쓰지 않아요. */
public class SimulationRequestDTO {

    @NotNull(message = "기준 결과를 골라 주세요")
    private Long basePredictionId; // 비교할 기준 결과 (PREDICTION.PREDICTION_ID · 본인 것인지 Service에서 확인)

    @NotBlank(message = "시나리오 이름을 입력해 주세요")
    @Size(max = 16, message = "시나리오 이름은 16자까지예요")
    private String scenarioName; // PREDICTION.SCENARIO_NAME

    @NotNull(message = "소득 정보를 보내 주세요")
    @Valid
    private IncomeRequestDTO income; // 소득 입력과 같은 칸 · 같은 검사

    @NotNull(message = "무주택 세대주 여부를 입력해 주세요")
    @Pattern(regexp = "[YN]", message = "무주택 세대주 여부는 Y 또는 N이에요")
    private String homelessHeadYn;

    @NotNull(message = "카드 사용액 목록을 보내 주세요")
    @Valid
    private List<CardSpendDTO> cardSpends = new ArrayList<>(); // 분류 + 결제수단별 금액

    public Long getBasePredictionId() {
        return basePredictionId;
    }

    public void setBasePredictionId(Long basePredictionId) {
        this.basePredictionId = basePredictionId;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public IncomeRequestDTO getIncome() {
        return income;
    }

    public void setIncome(IncomeRequestDTO income) {
        this.income = income;
    }

    public String getHomelessHeadYn() {
        return homelessHeadYn;
    }

    public void setHomelessHeadYn(String homelessHeadYn) {
        this.homelessHeadYn = homelessHeadYn;
    }

    public List<CardSpendDTO> getCardSpends() {
        return cardSpends;
    }

    public void setCardSpends(List<CardSpendDTO> cardSpends) {
        this.cardSpends = cardSpends;
    }
}
