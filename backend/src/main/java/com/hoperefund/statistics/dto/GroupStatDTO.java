package com.hoperefund.statistics.dto;

/** 그룹 비교 통계 1행 (GROUP_STAT) · 국세통계의 그룹별 1인당 평균이에요. 금액은 원 단위 정수예요. */
public class GroupStatDTO {

    private Integer statYear; // 통계 귀속 연도 (지금은 2024 · 귀속 연도보다 앞설 수 있음)
    private String groupLabel; // 그룹 이름 (화면 표시용 · 예: 여성 40대 · 4천만 이하)
    private Long peopleCount; // 그룹 인원
    private Long avgSalary; // 1인당 총급여
    private Long avgDecidedTax; // 1인당 결정세액
    private Long avgRefundTax; // 1인당 환급세액

    public Long getAvgRefundTax() {
        return avgRefundTax;
    }

    public Integer getStatYear() {
        return statYear;
    }

    public void setStatYear(Integer statYear) {
        this.statYear = statYear;
    }

    public String getGroupLabel() {
        return groupLabel;
    }

    public void setGroupLabel(String groupLabel) {
        this.groupLabel = groupLabel;
    }

    public Long getPeopleCount() {
        return peopleCount;
    }

    public void setPeopleCount(Long peopleCount) {
        this.peopleCount = peopleCount;
    }

    public Long getAvgSalary() {
        return avgSalary;
    }

    public void setAvgSalary(Long avgSalary) {
        this.avgSalary = avgSalary;
    }

    public Long getAvgDecidedTax() {
        return avgDecidedTax;
    }

    public void setAvgDecidedTax(Long avgDecidedTax) {
        this.avgDecidedTax = avgDecidedTax;
    }

    public void setAvgRefundTax(Long avgRefundTax) {
        this.avgRefundTax = avgRefundTax;
    }
}
