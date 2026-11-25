package com.hoperefund.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hoperefund.notification.service.FakeNotificationMapper;
import com.hoperefund.notification.service.NotificationService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

/** [추가 11/24 · 이우진 · TT-153 6단계] 상담 종 알림: 누구에게 · 보낼지 말지 (DB 없이 가짜 매퍼 · 구독 목록은 Mockito) */
class ConsultNotifierTest {

    private static final ConsultUser MEMBER = new ConsultUser(1002L, false);
    private static final ConsultUser ADMIN_1 = new ConsultUser(1L, true);
    private static final long ROOM = 12L;

    private ConsultServiceTest.FakeConsultMapper consultMapper;
    private FakeNotificationMapper notificationMapper;
    private SimpUserRegistry registry;
    private ConsultNotifier notifier;

    @BeforeEach
    void setUp() {
        consultMapper = new ConsultServiceTest.FakeConsultMapper();
        consultMapper.roomOwners.put(ROOM, MEMBER.memberId());
        consultMapper.activeAdmins.addAll(List.of(1L, 2L));
        notificationMapper = new FakeNotificationMapper();
        registry = mock(SimpUserRegistry.class); // 아무도 접속 안 함 (getUser → null)
        notifier =
                new ConsultNotifier(
                        consultMapper, new NotificationService(notificationMapper), registry);
    }

    // 그 회원이 destination 을 구독 중인 것처럼
    private void watching(long memberId, String destination) {
        SimpSubscription subscription = mock(SimpSubscription.class);
        when(subscription.getDestination()).thenReturn(destination);
        SimpSession session = mock(SimpSession.class);
        when(session.getSubscriptions()).thenReturn(Set.of(subscription));
        SimpUser user = mock(SimpUser.class);
        when(user.getSessions()).thenReturn(Set.of(session));
        when(registry.getUser(String.valueOf(memberId))).thenReturn(user);
    }

    private long countFor(long memberId) {
        return notificationMapper.rows.stream().filter(r -> r.memberId() == memberId).count();
    }

    @Test
    @DisplayName("회원이 보내면 활동 중인 관리자 전원에게 CHAT 알림 (내용은 넣지 않음)")
    void memberMessageNotifiesAllAdmins() {
        notifier.afterSend(MEMBER, ROOM);

        assertThat(notificationMapper.rows).hasSize(2);
        assertThat(countFor(1L)).isEqualTo(1);
        assertThat(countFor(2L)).isEqualTo(1);
        FakeNotificationMapper.Row row = notificationMapper.rows.get(0);
        assertThat(row.n().getNotiType()).isEqualTo(NotificationService.TYPE_CHAT);
        assertThat(row.n().getContent()).isEqualTo(ConsultNotifier.TO_ADMIN);
        assertThat(row.n().getLinkUrl()).isEqualTo("support");
    }

    @Test
    @DisplayName("관리자가 보내면 그 방 주인 회원에게만")
    void adminMessageNotifiesRoomOwnerOnly() {
        notifier.afterSend(ADMIN_1, ROOM);

        assertThat(notificationMapper.rows).hasSize(1);
        assertThat(countFor(MEMBER.memberId())).isEqualTo(1);
        assertThat(notificationMapper.rows.get(0).n().getContent())
                .isEqualTo(ConsultNotifier.TO_MEMBER);
    }

    @Test
    @DisplayName("받을 사람이 그 방을 보고 있으면 알림 안 보냄 · 다른 방을 보고 있으면 보냄")
    void skipsWhenWatchingThatRoom() {
        watching(MEMBER.memberId(), "/sub/consult/12");
        notifier.afterSend(ADMIN_1, ROOM);
        assertThat(notificationMapper.rows).isEmpty();

        watching(1L, "/sub/consult/13"); // 관리자 1은 다른 방을 보는 중
        watching(2L, "/sub/consult/12"); // 관리자 2는 이 방을 보는 중
        notifier.afterSend(MEMBER, ROOM);
        assertThat(countFor(1L)).isEqualTo(1);
        assertThat(countFor(2L)).isZero();
    }

    @Test
    @DisplayName("안 읽은 상담 알림이 있으면 메시지마다 쌓지 않음")
    void doesNotPileUpWhileUnread() {
        notifier.afterSend(ADMIN_1, ROOM);
        notifier.afterSend(ADMIN_1, ROOM);
        notifier.afterSend(ADMIN_1, ROOM);

        assertThat(countFor(MEMBER.memberId())).isEqualTo(1);
    }

    @Test
    @DisplayName("보낸 관리자 본인 · 없는 방에는 알림 안 만듦")
    void skipsSelfAndMissingRoom() {
        consultMapper.roomOwners.put(13L, 1L); // (흉내) 관리자 1이 주인인 방
        notifier.afterSend(ADMIN_1, 13L);
        notifier.afterSend(ADMIN_1, 999L);

        assertThat(notificationMapper.rows).isEmpty();
    }

    @Test
    @DisplayName("알림이 실패해도 예외를 던지지 않음 (메시지 보내기는 성공)")
    void neverThrows() {
        notificationMapper.fail = true;
        assertThatCode(() -> notifier.afterSend(MEMBER, ROOM)).doesNotThrowAnyException();

        when(registry.getUser("1")).thenThrow(new IllegalStateException("구독 목록 오류"));
        assertThatCode(() -> notifier.afterSend(MEMBER, ROOM)).doesNotThrowAnyException();
    }
}
