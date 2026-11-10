package com.hoperefund.prediction.client;

import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.prediction.dto.BenefitResponseDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.CardSpendDTO;
import com.hoperefund.prediction.dto.MemberQualificationDTO;
import java.util.List;

/**
 * 파이썬 서버(세액 계산)에 계산을 맡기는 창구 (인터페이스)
 *
 * <p>구현은 2개예요. application.yml의 calc.mode로 골라요. - mock: MockCalcClient (가짜 결과 · 파이썬 서버 없이 화면 연결용) -
 * http: HttpCalcClient (파이썬 서버 POST /calculate · /benefit) Service는 어느 쪽인지 몰라도 돼요 (스프링이 고른 구현을 주입 =
 * DI).
 */
public interface CalcClient {

    CalculationResponseDTO calculate(
            int taxYear,
            IncomeResponseDTO income,
            String homelessHeadYn,
            List<CardSpendDTO> cardSpends);

    BenefitResponseDTO diagnose(int taxYear, long totalSalary, MemberQualificationDTO member);
}
