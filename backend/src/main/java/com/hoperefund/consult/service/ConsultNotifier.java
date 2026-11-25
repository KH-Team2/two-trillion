package com.hoperefund.consult.service;

import com.hoperefund.consult.mapper.ConsultMapper;
import com.hoperefund.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * [추가 11/24 · 이우진 · TT-153 6단계] 상담 메시지 종 알림 (F-07-03 ④ · F-09-03 CHAT)
 *
 * <p>알림 통로는 기존 SSE 그대로 (NotificationService · 저장 → 접속 중이면 실시간). 여기는 「누구에게 · 보낼지 말지」만 정해요.
 *
 * <ul>
 *   <li>회원이 보내면 → 활동 중인 관리자 전원 · 관리자가 보내면 → 그 방 주인 회원
 *   <li>받을 사람이 지금 그 방을 구독 중이면(채팅 화면을 보고 있으면) 말풍선이 바로 뜨니까 알림 안 보냄
 *   <li>안 읽은 상담 알림(CHAT)이 이미 있으면 또 만들지 않음 (메시지마다 쌓이지 않게)
 *   <li>문구에 메시지 내용은 넣지 않음 (종 목록 · 미리보기에 상담 내용이 보이지 않게)
 *   <li>알림이 실패해도 메시지 보내기는 성공으로 둠 (로그만)
 * </ul>
 */
@Slf4j
@Component
public class ConsultNotifier {

    static final String ROOM_TOPIC_PREFIX = "/sub/consult/";
    static final String LINK_URL = "support"; // 고객센터 화면 (종 목록에서 누르면 이동)
    static final String TO_MEMBER = "상담사 답변이 도착했어요. 고객센터 「상담 채팅」에서 확인해 보세요.";
    static final String TO_ADMIN = "새 상담 메시지가 있어요. 고객센터 「상담 채팅」 방 목록에서 확인해 보세요.";

    private final ConsultMapper consultMapper;
    private final NotificationService notificationService;
    private final SimpUserRegistry userRegistry;

    public ConsultNotifier(
            ConsultMapper consultMapper,
            NotificationService notificationService,
            SimpUserRegistry userRegistry) {
        this.consultMapper = consultMapper;
        this.notificationService = notificationService;
        this.userRegistry = userRegistry;
    }

    /** 메시지가 저장 · 전달된 뒤 부름 */
    public void afterSend(ConsultUser sender, long roomId) {
        try {
            if (sender.admin()) {
                Long owner = consultMapper.findRoomOwner(roomId);
                if (owner != null && owner != sender.memberId()) {
                    notifyIfAway(owner, roomId, TO_MEMBER);
                }
                return;
            }
            for (Long adminId : consultMapper.findActiveAdminIds()) {
                if (adminId != null && adminId != sender.memberId()) {
                    notifyIfAway(adminId, roomId, TO_ADMIN);
                }
            }
        } catch (RuntimeException e) {
            log.warn("상담 알림 실패 room={} · {}", roomId, e.getClass().getSimpleName());
        }
    }

    private void notifyIfAway(long memberId, long roomId, String content) {
        if (isWatching(memberId, roomId)) {
            return;
        }
        notificationService.notifyMemberUnlessUnread(
                memberId, NotificationService.TYPE_CHAT, content, LINK_URL);
    }

    /**
     * 그 회원이 지금 이 방을 구독 중인지 (탭 여러 개 중 하나라도)
     *
     * <p>STOMP 연결의 사용자 이름 = CONNECT 때 붙인 로그인 정보의 이름 = 회원번호 문자열 (StompAuthChannelInterceptor)
     */
    boolean isWatching(long memberId, long roomId) {
        SimpUser user = userRegistry.getUser(String.valueOf(memberId));
        if (user == null) {
            return false;
        }
        String destination = ROOM_TOPIC_PREFIX + roomId;
        for (SimpSession session : user.getSessions()) {
            for (SimpSubscription subscription : session.getSubscriptions()) {
                if (destination.equals(subscription.getDestination())) {
                    return true;
                }
            }
        }
        return false;
    }
}
