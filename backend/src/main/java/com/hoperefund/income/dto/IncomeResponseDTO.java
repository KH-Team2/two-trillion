package com.hoperefund.income.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소득·공제 정보 조회/저장 결과를 화면에 돌려주는 DTO
 * - GET /api/incomes/{taxYear} · PUT /api/incomes/{taxYear} 응답
 * - 칸은 API 명세서 초안 v0.2의 응답 칸만 둔다 (incomeId · memberId · createdAt · dataOrigin은 화면에 안 보냄)
 */
@Getter // 화면으로 JSON을 만들 때 getter로 값을 읽음 · 응답은 바꿀 일이 없어서 setter는 안 둠
@NoArgsConstructor
@AllArgsConstructor
@Builder // IncomeResponseDTO.builder().칸이름(값).build() 처럼 칸 이름을 붙여 골라 넣으며 객체를 만드는 방식
public class IncomeResponseDTO {

    private Long totalSalary; // 연간 예상 총급여
    private Long withheldTax; // 기납부 소득세
    private Long nationalPension; // 국민연금보험료
    private Long healthInsurance; // 건강·고용·노인장기요양보험료
    private Integer dependents; // 부양가족 수
    private Integer children; // 20세 이하 자녀 수
    private Long monthlyRent; // 월 월세액
    private Long housingSaving; // 주택청약 연 납입액
    private Long pensionSaving; // 연금저축 연 납입액
    private Long irpSaving; // IRP 연 납입액
    private String smeReductionYn; // 중소기업 감면 신청 여부 Y/N
    private LocalDateTime updatedAt; // 수정일

    /**
     * DB에서 꺼낸 IncomeDTO를 화면용 IncomeResponseDTO로 바꿔 주는 메서드
     * - static: 객체를 만들지 않고 IncomeResponseDTO.from(income) 처럼 클래스 이름으로 바로 부를 수 있음
     * - service에서 mapper 조회 결과를 받아 이 메서드로 바꾼 뒤 controller로 넘긴다
     */
    public static IncomeResponseDTO from(IncomeDTO income) {
        return IncomeResponseDTO.builder()
                .totalSalary(income.getTotalSalary())
                .withheldTax(income.getWithheldTax())
                .nationalPension(income.getNationalPension())
                .healthInsurance(income.getHealthInsurance())
                .dependents(income.getDependents())
                .children(income.getChildren())
                .monthlyRent(income.getMonthlyRent())
                .housingSaving(income.getHousingSaving())
                .pensionSaving(income.getPensionSaving())
                .irpSaving(income.getIrpSaving())
                .smeReductionYn(income.getSmeReductionYn())
                .updatedAt(income.getUpdatedAt())
                .build();
    }
}