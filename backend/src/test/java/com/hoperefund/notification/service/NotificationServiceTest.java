package com.hoperefund.notification.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hoperefund.notification.dto.NotificationListDTO;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
    void 계산_완료_알림은_접속하지_않아도_저장된다() {
        service.notifyCalculation(81, 2026);

        assertEquals(1, mapper.rows.size());
        FakeNotificationMapper.Row row = mapper.rows.get(0);
        assertEquals(81, row.memberId());
        assertEquals(NotificationService.TYPE_CALCULATION, row.n().getNotiType());
        assertEquals("2026년 계산이 끝났어요. 눌러서 결과를 확인해 보세요.", row.n().getContent());
        assertEquals("dashboard", row.n().getLinkUrl());
    }

    @Test
    void 목록은_본인_것만_새것부터_안_읽은_개수와_함께() {
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
    void 모두_읽음은_본인_알림만_바꾼다() {
        service.notifyCalculation(81, 2026);
        service.notifyCalculation(82, 2026);

        service.markAllRead(81);

        assertEquals(0, service.getMyNotifications(81).unreadCount());
        assertEquals(1, service.getMyNotifications(82).unreadCount());
    }

    @Test
    void 마감_안내는_활동_중인_회원_모두에게_저장된다() {
        mapper.activeMembers = Set.of(81L, 82L, 83L);

        int saved =
                service.notifyAllActive(NotificationService.TYPE_DEADLINE, "마감 7일 전이에요.", "input");

        assertEquals(3, saved);
        assertEquals(1, service.getMyNotifications(82).unreadCount());
    }

    @Test
    void 저장이_실패해도_예외를_던지지_않는다() {
        mapper.fail = true;

        assertDoesNotThrow(() -> service.notifyCalculation(81, 2026));
        assertEquals(0, service.notifyAllActive(NotificationService.TYPE_DEADLINE, "안내", "input"));
    }

    @Test
    void Last_Event_ID는_양수_숫자만_받는다() {
        assertEquals(12L, NotificationService.parseId("12"));
        assertEquals(12L, NotificationService.parseId(" 12 "));
        assertNull(NotificationService.parseId(null));
        assertNull(NotificationService.parseId(""));
        assertNull(NotificationService.parseId("abc"));
        assertNull(NotificationService.parseId("-1"));
        assertNull(NotificationService.parseId("0"));
    }

    @Test
    void 다시_연결할_때_조회가_실패해도_연결은_만들어진다() {
        service.notifyCalculation(81, 2026);
        mapper.fail = true;

        assertNotNull(service.subscribe(81, "1"));
    }
}
