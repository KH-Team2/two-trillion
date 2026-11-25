package com.hoperefund.chatbot.dto;

// 맞춤 절세 가이드의 추천 조건 판단에 필요한 입력값
public class GuideInputDTO {

    private Long totalSalary;
    private String homelessHeadYn;
    private Long housingSaving;
    private Long pensionSaving;
    private Long irpSaving;

    public Long getTotalSalary() {
        return totalSalary;
    }

    public void setTotalSalary(Long totalSalary) {
        this.totalSalary = totalSalary;
    }

    public String getHomelessHeadYn() {
        return homelessHeadYn;
    }

    public void setHomelessHeadYn(String homelessHeadYn) {
        this.homelessHeadYn = homelessHeadYn;
    }

    public Long getHousingSaving() {
        return housingSaving;
    }

    public void setHousingSaving(Long housingSaving) {
        this.housingSaving = housingSaving;
    }

    public Long getPensionSaving() {
        return pensionSaving;
    }

    public void setPensionSaving(Long pensionSaving) {
        this.pensionSaving = pensionSaving;
    }

    public Long getIrpSaving() {
        return irpSaving;
    }

    public void setIrpSaving(Long irpSaving) {
        this.irpSaving = irpSaving;
    }
}
