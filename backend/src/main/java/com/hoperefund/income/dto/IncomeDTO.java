package com.hoperefund.income.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * INCOME 테이블 한 행(연도별 소득·납입 정보)을 담는 DTO
 * - DTO(Data Transfer Object): DB ↔ 자바 사이에서 데이터를 옮겨 담는 "상자" 역할의 클래스
 * - MyBatis가 SELECT 결과를 이 클래스에 담아 주고, INSERT/UPDATE 때 여기서 값을 꺼내 쓴다
 * - DB 칸 이름(TOTAL_SALARY)과 자바 필드 이름(totalSalary)은
 *   MyBatis 설정 map-underscore-to-camel-case: true 가 있으면 자동으로 연결된다
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // IncomeDTO.builder().totalSalary(30000000L).build() 처럼 원하는 칸만 골라 만들 수 있게 해 줌
public class IncomeDTO {
	
    private Long incomeId; // 소득번호 (PK)
    private Long memberId; // 회원번호 → MEMBER
    private Integer taxYear; // 귀속 연도 NUMBER(4)
    private Long totalSalary; // 연간 예상 총급여 (원)
    private Long withheldTax; // 기납부 소득세 (원)
    private Long nationalPension; // 국민연금보험료 본인 부담 (원)
    private Integer dependents; // 부양가족 수
    private Long monthlyRent; // 월 월세액 (원)
    private Long housingSaving; // 주택청약 연 납입액 (원)
    private Long pensionSaving; // 연금저축 연 납입액 (원)
    private String smeReductionYn; // 중소기업 감면 신청 여부 'Y'/'N' (CHAR(1) → String)
    private LocalDateTime updatedAt; // 수정일 (Oracle DATE는 시·분·초까지 있어서 LocalDateTime)
    private LocalDateTime createdAt; // 등록일
    private String dataOrigin; // 데이터 구분 COLLECTED/GENERATED/INPUT
    private Integer children; // 20세 이하 자녀 수
    private Long healthInsurance; // 건강·고용·노인장기요양보험료 본인 부담 (원)
    private Long irpSaving; // IRP(퇴직연금계좌) 연 납입액 (원)
    
}