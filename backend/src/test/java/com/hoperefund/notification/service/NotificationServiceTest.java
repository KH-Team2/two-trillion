package com.hoperefund.notification.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hoperefund.notification.dto.NotificationListDTO;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 알림 저장 · 목록 · 읽음 (TT-158) · DB 대신 가짜 매퍼 */
class NotificationServiceTest {

    private FakeNotificationMapper mapper;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeNotificationMapper();
        service = new NotificationService(mapper);
    }

    @Test
    @DisplayName("계산 완료 알림은 접속하지 않아도 저장된다")
    void savesCalculationNoticeEvenWhenNotConnected() {
        service.notifyCalculation(81, 2026);

        assertEquals(1, mapper.rows.size());
        FakeNotificationMapper.Row row = mapper.rows.get(0);
        assertEquals(81, row.memberId());
        assertEquals(NotificationService.TYPE_CALCULATION, row.n().getNotiType());
        assertEquals("2026년 계산이 끝났어요. 눌러서 결과를 확인해 보세요.", row.n().getContent());
        assertEquals("dashboard", row.n().getLinkUrl());
    }

    @Test
    @DisplayName("목록은 본인 것만 새것부터 안 읽은 개수와 함께")
    void listsOnlyOwnNoticesNewestFirstWithUnreadCount() {
        service.notifyCalculation(81, 2025);
        service.notifyCalculation(82, 2026); // 다른 회원
        service.notifyCalculation(81, 2026);

        NotificationListDTO list = service.getMyNotifications(81);

        assertEquals(2, list.unreadCount());
        assertEquals(2, list.items().size());
        assertTrue(list.items().get(0).getContent().startsWith("2026년"));
        assertTrue(list.items().get(1).getContent().startsWith("2025년"));
    }

    @Test
    @DisplayName("모두 읽음은 본인 알림만 바꾼다")
    void markAllReadChangesOnlyOwnNotices() {
        service.notifyCalculation(81, 2026);
        service.notifyCalculation(82, 2026);

        service.markAllRead(81);

        assertEquals(0, service.getMyNotifications(81).unreadCount());
        assertEquals(1, service.getMyNotifications(82).unreadCount());
    }

    @Test
    @DisplayName("마감 안내는 활동 중인 회원 모두에게 저장된다")
    void savesDeadlineNoticeForAllActiveMembers() {
        mapper.activeMembers = Set.of(81L, 82L, 83L);

        int saved =
                service.notifyAllActive(NotificationService.TYPE_DEADLINE, "마감 7일 전이에요.", "input");

        assertEquals(3, saved);
        assertEquals(1, service.getMyNotifications(82).unreadCount());
    }

    @Test
    @DisplayName("저장이 실패해도 예외를 던지지 않는다")
    void doesNotThrowWhenSaveFails() {
        mapper.fail = true;

        assertDoesNotThrow(() -> service.notifyCalculation(81, 2026));
        assertEquals(0, service.notifyAllActive(NotificationService.TYPE_DEADLINE, "안내", "input"));
    }

    @Test
    @DisplayName("Last-Event-ID는 양수 숫자만 받는다")
    void acceptsOnlyPositiveNumberAsLastEventId() {
        assertEquals(12L, NotificationService.parseId("12"));
        assertEquals(12L, NotificationService.parseId(" 12 "));
        assertNull(NotificationService.parseId(null));
        assertNull(NotificationService.parseId(""));
        assertNull(NotificationService.parseId("abc"));
        assertNull(NotificationService.parseId("-1"));
        assertNull(NotificationService.parseId("0"));
    }

    @Test
    @DisplayName("다시 연결할 때 조회가 실패해도 연결은 만들어진다")
    void subscribesEvenWhenMissedNoticeLookupFails() {
        service.notifyCalculation(81, 2026);
        mapper.fail = true;

        assertNotNull(service.subscribe(81, "1"));
    }
}
