package com.hoperefund.prediction.dto;

import java.util.List;

/**
 * 계산 서버 POST /calculate 요청 본문.
 *
 * <p>계산 서버는 모르는 칸이 오면 400으로 거부하므로, 받는 칸만 여기에 둔다. 칸 이름은 계산 서버의 {@code
 * calc/app/schemas/calculation.py}와 같아야 한다.
 */
public record CalcRequestDTO(
        int taxYear,
        Long totalSalary,
        Long withheldTax,
        Long nationalPension,
        Long healthInsurance,
        Integer dependents,
        Integer children,
        Long monthlyRent,
        Long housingSaving,
        Long pensionSaving,
        Long irpSaving,
        String smeReductionYn,
        String homelessHeadYn,
        List<CardSpendDTO> cardSpends) {}
