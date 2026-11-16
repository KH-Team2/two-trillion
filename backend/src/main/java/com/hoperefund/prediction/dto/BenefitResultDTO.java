package com.hoperefund.prediction.dto;

import java.util.ArrayList;
import java.util.List;

/** 혜택 1개의 진단 결과 (eligible: true 해당 · false 비해당 · null 정보 부족) */
public class BenefitResultDTO {

    private String benefitCode;
    private String benefitName;
    private Boolean eligible;
    private List<String> reasons = new ArrayList<>(); // 떨어진 조건 또는 입력 안내
    private String sourceNote; // 근거 (국세청 신고안내 쪽수 · 조문)

    public String getBenefitCode() {
        return benefitCode;
    }

    public void setBenefitCode(String benefitCode) {
        this.benefitCode = benefitCode;
    }

    public String getBenefitName() {
        return benefitName;
    }

    public void setBenefitName(String benefitName) {
        this.benefitName = benefitName;
    }

    public Boolean getEligible() {
        return eligible;
    }

    public void setEligible(Boolean eligible) {
        this.eligible = eligible;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public void setSourceNote(String sourceNote) {
        this.sourceNote = sourceNote;
    }
}
