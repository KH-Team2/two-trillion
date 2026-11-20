package com.hoperefund.notification.dto;

/**
 * 계산 완료 알림 내용 (TT-150) · 화면에서 토스트로 보여 줘요.
 *
 * <p>record: 값만 담는 클래스를 짧게 쓰는 방법 (Java 16+). getter · 생성자를 자동으로 만들어 줘요.
 *
 * @param expectedRefund 양수 = 환급, 음수 = 추가 납부
 */
public record CalculationNotificationDTO(int taxYear, Long predictionId, Long expectedRefund) {}
