package com.hoperefund.income.dto;

import java.time.LocalDateTime;

/** 소득 조회 응답 (INCOME 1행). MyBatis가 칸 이름을 camelCase로 바꿔 채워요. */
public class IncomeResponseDTO {

    private Long incomeId;
    private Integer taxYear;
    private Long totalSalary;
    private Long withheldTax;
    private Long nationalPension;
    private Long healthInsurance;
    private Integer dependents;
    private Integer children;
    private Long monthlyRent;
    private Long housingSaving;
    private Long pensionSaving;
    private Long irpSaving;
    private String smeReductionYn;
    private LocalDateTime updatedAt;

    public Long getIncomeId() {
        return incomeId;
    }

    public void setIncomeId(Long incomeId) {
        this.incomeId = incomeId;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
