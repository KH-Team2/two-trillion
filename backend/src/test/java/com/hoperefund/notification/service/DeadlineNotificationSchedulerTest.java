package com.hoperefund.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 공제 마감 임박 알림 (TT-152) · 날짜를 넣어서 12월을 기다리지 않고 확인 */
class DeadlineNotificationSchedulerTest {

    private final DeadlineNotificationScheduler scheduler =
            new DeadlineNotificationScheduler(new NotificationService());

    @Test
    void D30_D7_D1에만_보낸다() {
        assertTrue(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 1))); // D-30
        assertTrue(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 24))); // D-7
        assertTrue(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 30))); // D-1
    }

    @Test
    void 다른_날은_보내지_않는다() {
        assertFalse(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 21))); // D-10
        assertFalse(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 31))); // 당일
    }

    @Test
    void 문구에_금액이_없고_남은_날짜가_있다() {
        assertEquals(
                "2026년 사용분은 12월 31일까지 반영돼요. 마감 7일 전이에요.",
                DeadlineNotificationScheduler.messageFor(LocalDate.of(2026, 12, 24)));
        assertNull(DeadlineNotificationScheduler.messageFor(LocalDate.of(2026, 11, 20)));
    }
}
