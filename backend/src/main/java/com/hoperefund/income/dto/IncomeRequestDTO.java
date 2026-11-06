package com.hoperefund.income.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 화면에서 소득·공제 정보를 받을 때 쓰는 DTO
 * - PUT /api/incomes/{taxYear} 의 본문(JSON)
 * - 화면이 넣어야 하는 칸만 둔다 (백엔드 규칙)
 *   · taxYear: 주소 {taxYear}로 받음
 *   · incomeId · memberId · updatedAt · createdAt · dataOrigin: 서버가 정하는 값이라 화면에서 못 바꾸게 뺌
 * - 화면이 보낸 JSON을 스프링이 빈 생성자로 객체를 만든 뒤 setter로 칸을 채워 넣는다
 */
@Getter
@Setter
@NoArgsConstructor
public class IncomeRequestDTO {

    private Long totalSalary; // 연간 예상 총급여 (필수)
    private Long withheldTax; // 기납부 소득세 (필수)
    private Long nationalPension; // 국민연금보험료
    private Long healthInsurance; // 건강·고용·노인장기요양보험료
    private Integer dependents; // 부양가족 수
    private Integer children; // 20세 이하 자녀 수
    private Long monthlyRent; // 월 월세액
    private Long housingSaving; // 주택청약 연 납입액
    private Long pensionSaving; // 연금저축 연 납입액
    private Long irpSaving; // IRP 연 납입액
    private String smeReductionYn; // 중소기업 감면 신청 여부 Y/N
}