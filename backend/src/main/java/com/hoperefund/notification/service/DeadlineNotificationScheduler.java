package com.hoperefund.notification.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공제 마감 임박 알림 (2차 미션 · TT-152).
 *
 * <p>계산은 그해 1/1 ~ 12/31 사용분만 넣어요 (PredictionMapper). 그래서 12/31을 마감으로 보고 D-30 · D-7 · D-1 에만
 * 안내해요. 문구에 금액 같은 금융 정보는 넣지 않아요.
 */
@Component
public class DeadlineNotificationScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<Long> NOTICE_DAYS = Set.of(30L, 7L, 1L);

    private final NotificationService notificationService;

    public DeadlineNotificationScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // 초 분 시 일 월 요일 → 12월 매일 09:00 (서울 시간) · @EnableScheduling 은 NotificationSchedulingConfig
    @Scheduled(cron = "0 0 9 * 12 *", zone = "Asia/Seoul")
    public void run() {
        notifyIfNeeded(LocalDate.now(SEOUL));
    }

    // 날짜를 받게 나눠서, 테스트에서 12월을 기다리지 않고 확인해요
    boolean notifyIfNeeded(LocalDate today) {
        String message = messageFor(today);
        if (message == null) {
            return false;
        }
        // [수정 11/23 · 이우진 · TT-158] 활동 중인 회원 모두에게 저장 → 접속 중인 회원에게는 바로 보내요
        notificationService.notifyAllActive(NotificationService.TYPE_DEADLINE, message, "input");
        return true;
    }

    // 보낼 날이 아니면 null
    static String messageFor(LocalDate today) {
        LocalDate deadline = LocalDate.of(today.getYear(), 12, 31);
        long daysLeft = ChronoUnit.DAYS.between(today, deadline);
        if (!NOTICE_DAYS.contains(daysLeft)) {
            return null;
        }
        return today.getYear() + "년 사용분은 12월 31일까지 반영돼요. 마감 " + daysLeft + "일 전이에요.";
    }
}
