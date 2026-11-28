package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 예측 결과 목록 한 줄 (F-08-02 ② · S-08) · [신규 11/26 고은]
 *
 * <p>DTO(Data Transfer Object): DB에서 읽은 값을 화면(JSON)으로 보낼 때 담는 상자예요.
 *
 * <p>- @Getter · @Setter (롬복): getXxx() · setXxx()를 자동으로 만들어 줘요. MyBatis는 setter로 값을 채우고, JSON으로 바꿀 때는
 * getter로 꺼내요.
 *
 * <p>- 금액 칸은 Long: NUMBER(12)는 최대 9,999억이라 int(약 21억)를 넘을 수 있어요.
 */
@Getter
@Setter
public class AdminPredictionResponseDto {

    private Long predictionId; // 예측 번호 (PREDICTION_ID)
    private Long memberId; // 회원 번호
    private String email; // 이메일 일부 가림 (예: te***@naver.com · 회원 관리 탭과 같은 규칙)
    private Integer taxYear; // 귀속 연도
    private String scenarioName; // 「현재」(처음 계산) 또는 시나리오 이름(조건 변경)
    private Long basePredictionId; // 시나리오가 기준으로 삼은 예측 번호 (처음 계산이면 null)
    private Long totalSalary; // 계산에 쓴 총급여 (PREDICTION_INPUT · 입력값 행이 없으면 null)
    private Long taxableIncome; // 과세표준
    private Long calculatedTax; // 산출세액
    private Long decidedTax; // 결정세액
    private Long expectedRefund; // 예상 환급액 (음수 = 추가 납부)
    private String predictedAt; // 계산 일시 'YYYY-MM-DD HH24:MI'
}
