package com.hoperefund.prediction.dto;

import java.time.LocalDate;

/** 혜택 진단에 쓰는 회원 자격 정보 (MEMBER에서 읽기만 · 화면으로 내보내지 않음) */
public class MemberQualificationDTO {

    private LocalDate birthDate; // 생년월일
    private Integer militaryMonths; // 군 복무 개월
    private LocalDate hireDate; // 입사일 (없을 수 있음)
    private String companySize; // SME · MID · LARGE (없을 수 있음)
    private String homelessHeadYn; // 무주택 세대주 Y/N

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Integer getMilitaryMonths() {
        return militaryMonths;
    }

    public void setMilitaryMonths(Integer militaryMonths) {
        this.militaryMonths = militaryMonths;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public String getHomelessHeadYn() {
        return homelessHeadYn;
    }

    public void setHomelessHeadYn(String homelessHeadYn) {
        this.homelessHeadYn = homelessHeadYn;
    }
}
