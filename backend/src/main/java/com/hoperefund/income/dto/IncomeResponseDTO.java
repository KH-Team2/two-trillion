package com.hoperefund.income.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 화면에 소득·공제 정보를 돌려줄 때 쓰는 DTO
 *
 * <p>- GET · PUT /api/incomes/{taxYear} 의 응답(JSON)
 *
 * <p>- 칸은 API 명세서 그대로 ([수정 11/12] incomeId · taxYear 추가 · memberId는 화면에 안 보냄)
 *
 * <p>- MyBatis가 SELECT 결과를 setter로 채운다 (application.yml의 map-underscore-to-camel-case 설정 덕분에
 * TOTAL_SALARY → totalSalary 처럼 이름이 자동으로 맞춰짐)
 */
@Getter
@Setter
@NoArgsConstructor
public class IncomeResponseDTO {

    // DB 칸이 전부 NOT NULL이라 null이 올 일이 없어서 기본형(long · int)을 쓴다

    // [수정 11/12] incomeId · taxYear 추가 · 이유: API 명세서(노션 6번) 소득 행 응답에 들어 있는 칸 (팀원 PR #7과 같게)
    private long incomeId; // 소득번호
    private int taxYear; // 귀속 연도

    private long totalSalary; // 연간 예상 총급여
    private long withheldTax; // 기납부 소득세
    private long nationalPension; // 국민연금보험료
    private long healthInsurance; // 건강·고용·노인장기요양보험료
    private int dependents; // 부양가족 수
    private int children; // 20세 이하 자녀 수
    private long monthlyRent; // 월 월세액
    private long housingSaving; // 주택청약 연 납입액
    private long pensionSaving; // 연금저축 연 납입액
    private long irpSaving; // IRP 연 납입액
    private String smeReductionYn; // 중소기업 감면 신청 여부 Y/N
    private LocalDateTime updatedAt; // 마지막 수정일 (LocalDateTime: 날짜 + 시간을 담는 자바 타입)
}
