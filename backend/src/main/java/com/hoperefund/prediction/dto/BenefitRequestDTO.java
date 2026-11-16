package com.hoperefund.prediction.dto;

/**
 * 계산 서버 POST /benefit 요청 본문.
 *
 * <p>계산 서버는 모르는 칸이 오면 400으로 거부하므로, 받는 칸만 여기에 둔다. 칸 이름은 계산 서버의 {@code
 * calc/app/schemas/benefit.py}와 같아야 한다. 날짜는 {@code "2024-03-02"} 모양의 글자로 보낸다.
 */
public record BenefitRequestDTO(
        int taxYear,
        long totalSalary,
        String birthDate,
        int militaryMonths,
        String hireDate,
        String companySize,
        String homelessHeadYn) {}
