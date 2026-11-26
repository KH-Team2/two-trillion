package com.hoperefund.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 공제 마감 임박 알림 (TT-152) · 날짜를 넣어서 12월을 기다리지 않고 확인 */
class DeadlineNotificationSchedulerTest {

    private final FakeNotificationMapper mapper = new FakeNotificationMapper();
    private final DeadlineNotificationScheduler scheduler =
            new DeadlineNotificationScheduler(new NotificationService(mapper));

    @Test
    @DisplayName("D-30 · D-7 · D-1에만 보낸다")
    void sendsOnlyOnD30D7D1() {
        assertTrue(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 1))); // D-30
        assertTrue(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 24))); // D-7
        assertTrue(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 30))); // D-1
    }

    @Test
    @DisplayName("보낼 날에는 활동 중인 회원에게 저장된다")
    void savesForActiveMembersOnNoticeDay() {
        mapper.activeMembers = Set.of(81L, 82L);

        scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 24)); // D-7

        assertEquals(2, mapper.rows.size());
        assertEquals(NotificationService.TYPE_DEADLINE, mapper.rows.get(0).n().getNotiType());
        assertEquals("input", mapper.rows.get(0).n().getLinkUrl());
    }

    @Test
    @DisplayName("다른 날은 보내지 않는다")
    void doesNotSendOnOtherDays() {
        assertFalse(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 21))); // D-10
        assertFalse(scheduler.notifyIfNeeded(LocalDate.of(2026, 12, 31))); // 당일
    }

    @Test
    @DisplayName("문구에 금액이 없고 남은 날짜가 있다")
    void messageHasDaysLeftAndNoAmount() {
        assertEquals(
                "2026년 사용분은 12월 31일까지 반영돼요. 마감 7일 전이에요.",
                DeadlineNotificationScheduler.messageFor(LocalDate.of(2026, 12, 24)));
        assertNull(DeadlineNotificationScheduler.messageFor(LocalDate.of(2026, 11, 20)));
    }
}
