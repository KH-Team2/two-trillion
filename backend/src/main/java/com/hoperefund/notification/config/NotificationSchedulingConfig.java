package com.hoperefund.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 알림 하트비트용 예약 작업 켜기 (TT-150 · NotificationService.heartbeat).
 *
 * <p>@Scheduled 는 @EnableScheduling 이 있어야 동작해요. 알림 기능 안에 두어서 다른 파트 설정은 건드리지 않아요.
 */
@Configuration
@EnableScheduling
public class NotificationSchedulingConfig {}
