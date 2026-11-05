package com.hoperefund.income.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 소득 입력 요청 (화면 S-02 → INCOME). 금액은 원 단위 정수, 모두 0 이상 (DB CHECK CK_INCOME_AMOUNT와 같은 규칙). */
public class IncomeRequestDTO {

    @NotNull(message = "총급여를 입력해 주세요")
    @Min(value = 0, message = "총급여는 0원 이상이어야 해요")
    @Max(value = 999_999_999_999L, message = "총급여가 너무 커요")
    private Long totalSalary;

    @NotNull(message = "기납부세액을 입력해 주세요")
    @Min(value = 0, message = "기납부세액은 0원 이상이어야 해요")
    @Max(value = 999_999_999_999L, message = "기납부세액이 너무 커요")
    private Long withheldTax;

    @NotNull(message = "국민연금보험료를 입력해 주세요")
    @Min(value = 0, message = "국민연금보험료는 0원 이상이어야 해요")
    @Max(value = 999_999_999_999L, message = "금액이 너무 커요")
    private Long nationalPension = 0L;

    @NotNull(message = "건강·고용보험료를 입력해 주세요")
    @Min(value = 0, message = "건강·고용보험료는 0원 이상이어야 해요")
    @Max(value = 999_999_999_999L, message = "금액이 너무 커요")
    private Long healthInsurance = 0L;

    @NotNull(message = "부양가족 수를 입력해 주세요")
    @Min(value = 0, message = "부양가족 수는 0명 이상이어야 해요")
    @Max(value = 99, message = "부양가족 수가 너무 커요")
    private Integer dependents = 0;

    @NotNull(message = "자녀 수를 입력해 주세요")
    @Min(value = 0, message = "자녀 수는 0명 이상이어야 해요")
    @Max(value = 99, message = "자녀 수가 너무 커요")
    private Integer children = 0;

    @NotNull(message = "월세를 입력해 주세요")
    @Min(value = 0, message = "월세는 0원 이상이어야 해요")
    @Max(value = 9_999_999_999L, message = "금액이 너무 커요")
    private Long monthlyRent = 0L;

    @NotNull(message = "주택청약 납입액을 입력해 주세요")
    @Min(value = 0, message = "주택청약 납입액은 0원 이상이어야 해요")
    @Max(value = 9_999_999_999L, message = "금액이 너무 커요")
    private Long housingSaving = 0L;

    @NotNull(message = "연금저축 납입액을 입력해 주세요")
    @Min(value = 0, message = "연금저축 납입액은 0원 이상이어야 해요")
    @Max(value = 9_999_999_999L, message = "금액이 너무 커요")
    private Long pensionSaving = 0L;

    @NotNull(message = "IRP 납입액을 입력해 주세요")
    @Min(value = 0, message = "IRP 납입액은 0원 이상이어야 해요")
    @Max(value = 9_999_999_999L, message = "금액이 너무 커요")
    private Long irpSaving = 0L;

    @NotNull(message = "중소기업 감면 신청 여부를 입력해 주세요")
    @Pattern(regexp = "[YN]", message = "중소기업 감면 신청 여부는 Y 또는 N이에요")
    private String smeReductionYn = "N";

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

    public Long getNationalPension() {
        return nationalPension;
    }

    public void setNationalPension(Long nationalPension) {
        this.nationalPension = nationalPension;
    }

    public Long getHealthInsurance() {
        return healthInsurance;
    }

    public void setHealthInsurance(Long healthInsurance) {
        this.healthInsurance = healthInsurance;
    }

    public Integer getDependents() {
        return dependents;
    }

    public void setDependents(Integer dependents) {
        this.dependents = dependents;
    }

    public Integer getChildren() {
        return children;
    }

    public void setChildren(Integer children) {
        this.children = children;
    }

    public Long getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(Long monthlyRent) {
        this.monthlyRent = monthlyRent;
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

    public String getSmeReductionYn() {
        return smeReductionYn;
    }

    public void setSmeReductionYn(String smeReductionYn) {
        this.smeReductionYn = smeReductionYn;
    }
}
