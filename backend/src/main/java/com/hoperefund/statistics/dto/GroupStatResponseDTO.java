package com.hoperefund.statistics.dto;

/**
 * 그룹 비교 통계 응답 (F-06-02 · 「나와 비슷한 사람들은」) · 그룹을 못 찾으면 그 칸만 null이에요 (성별 미입력 · 소득 미저장 등 · 오류로 보지
 * 않음).
 */
public class GroupStatResponseDTO {

    private Integer taxYear; // 귀속 연도 (요청한 연도)
    private GroupStatDTO ageGroup; // 본인 성별·연령대 그룹 (없으면 null)
    private GroupStatDTO salaryGroup; // 본인 총급여 구간 그룹 (없으면 null)

    // 기본 생성자: JSON으로 바꿀 때(직렬화) 라이브러리가 사용해요
    public GroupStatResponseDTO() {}

    public GroupStatResponseDTO(Integer taxYear, GroupStatDTO ageGroup, GroupStatDTO salaryGroup) {
        this.taxYear = taxYear;
        this.ageGroup = ageGroup;
        this.salaryGroup = salaryGroup;
    }

    public Integer getTaxYear() {
        return taxYear;
    }

    public void setTaxYear(Integer taxYear) {
        this.taxYear = taxYear;
    }

    public GroupStatDTO getAgeGroup() {
        return ageGroup;
    }

    public void setAgeGroup(GroupStatDTO ageGroup) {
        this.ageGroup = ageGroup;
    }

    public GroupStatDTO getSalaryGroup() {
        return salaryGroup;
    }

    public void setSalaryGroup(GroupStatDTO salaryGroup) {
        this.salaryGroup = salaryGroup;
    }
}
