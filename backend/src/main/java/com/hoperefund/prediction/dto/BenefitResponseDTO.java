package com.hoperefund.prediction.dto;

import java.util.ArrayList;
import java.util.List;

/** 청년 혜택 자격 진단 응답 (F-03-02 · 분담표 F-12 · S-05) */
public class BenefitResponseDTO {

    private Integer taxYear;
    private List<BenefitResultDTO> results = new ArrayList<>();

    public Integer getTaxYear() {
        return taxYear;
    }

    public void setTaxYear(Integer taxYear) {
        this.taxYear = taxYear;
    }

    public List<BenefitResultDTO> getResults() {
        return results;
    }

    public void setResults(List<BenefitResultDTO> results) {
        this.results = results;
    }
}
