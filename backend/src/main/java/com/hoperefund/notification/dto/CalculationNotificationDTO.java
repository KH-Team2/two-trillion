package com.hoperefund.notification.dto;

/**
 * 계산 완료 알림 내용 (TT-150) · 화면에서 토스트로 보여 줘요.
 *
 * <p>record: 값만 담는 클래스를 짧게 쓰는 방법 (Java 16+). getter · 생성자를 자동으로 만들어 줘요.
 *
 * <p>[수정 11/23] 금액(expectedRefund)은 보내지 않아요 (알림에 금융 정보를 넣지 않음) · 금액은 대시보드에서 확인
 */
public record CalculationNotificationDTO(int taxYear, Long predictionId) {}
