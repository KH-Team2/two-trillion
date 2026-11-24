package com.hoperefund.notification.service;

import com.hoperefund.notification.dto.NotificationDTO;
import com.hoperefund.notification.dto.NotificationListDTO;
import com.hoperefund.notification.mapper.NotificationMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 알림 (2차 미션 · TT-150) + 알림 저장 · 목록 (TT-158).
 *
 * <p>회원 번호 → 열려 있는 연결 목록을 메모리에 보관해요. 같은 회원이 탭을 여러 개 열면 연결도 여러 개예요. 서버를 재시작하면 목록이 사라지고,
 * 화면이 다시 연결해요.
 *
 * <p>[추가 11/23 · 이우진 · TT-158] 알림은 보내기 전에 NOTIFICATION 에 먼저 저장해요 → 접속 안 한 회원도 다음에 종 아이콘 목록에서 볼
 * 수 있고, 연결이 끊겼던 동안의 알림은 다시 연결할 때(Last-Event-ID) 받아 가요.
 */
@Service
public class NotificationService {

    /** SSE 이벤트 이름 · data 는 NotificationDTO(JSON) · id 는 알림 번호 */
    public static final String EVENT = "notification";

    public static final String TYPE_CALCULATION = "CALCULATION";
    public static final String TYPE_DEADLINE = "DEADLINE";
    public static final String TYPE_CHAT = "CHAT"; // [추가 11/24 · 이우진 · TT-153] 상담 채팅

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final long TIMEOUT_MS = 30L * 60 * 1000; // 30분 지나면 연결을 닫음 → 화면이 다시 연결
    private static final long HEARTBEAT_MS = 25_000; // 할 말이 없어도 25초마다 신호 (화면은 60초 조용하면 다시 연결)
    private static final int LIST_LIMIT = 20; // 종 아이콘 목록에 보여 줄 최근 알림 개수
    // [추가 11/24 · 이우진 · 코드 리뷰] 한 회원이 열어 둘 수 있는 연결(탭) 수 · 넘으면 가장 오래된 연결을 닫음
    // 이유: 연결을 계속 열면 서버 메모리 · 연결 자원이 30분씩 묶임 (정상 사용은 탭 몇 개)
    static final int MAX_CONNECTIONS_PER_MEMBER = 5;

    private final NotificationMapper notificationMapper;

    // 여러 요청이 동시에 넣고 빼도 안전한 Map · 목록
    // [수정 11/24] Set → Deque(들어온 순서 유지) · 가장 오래된 연결을 앞에서 꺼내 닫으려고
    private final Map<Long, Deque<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /**
     * 구독: 연결을 하나 만들어 목록에 넣고 돌려줘요.
     *
     * @param lastEventId 화면이 마지막으로 받은 알림 번호 (다시 연결할 때 Last-Event-ID 헤더 · 처음이면 null) → 그 뒤 알림을 바로
     *     다시 보내요
     */
    public SseEmitter subscribe(long memberId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Deque<SseEmitter> list =
                emitters.compute(
                        memberId,
                        (id, current) -> {
                            Deque<SseEmitter> next =
                                    current == null ? new ConcurrentLinkedDeque<>() : current;
                            next.addLast(emitter);
                            return next;
                        });
        closeOldest(memberId, list);

        // 연결이 끝나면(정상 종료 · 시간 초과 · 오류) 목록에서 빼요 → 메모리가 계속 늘지 않게
        Runnable remove = () -> removeEmitter(memberId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());

        // 연결되자마자 한 번 보내야 브라우저가 「연결됨」을 바로 알아요
        send(memberId, emitter, SseEmitter.event().name("connected").data("ok"));
        replay(memberId, emitter, parseId(lastEventId));
        log.info("알림 연결 member={} 연결 수={}", memberId, list.size());
        return emitter;
    }

    /** 계산 완료 알림 · 문구에 금액은 넣지 않아요 (금액은 눌러서 이동한 대시보드에서) */
    public void notifyCalculation(long memberId, int taxYear) {
        notifyMember(
                memberId, TYPE_CALCULATION, taxYear + "년 계산이 끝났어요. 눌러서 결과를 확인해 보세요.", "dashboard");
    }

    /**
     * 한 회원에게: 저장 → 열려 있는 연결(탭)에 보내기.
     *
     * <p>저장이 실패해도(DB 오류) 예외를 밖으로 던지지 않아요 → 알림 때문에 계산 요청이 실패하면 안 되니까요. 그때는 번호 없이 지금 열린 화면에만
     * 보내요.
     */
    public void notifyMember(long memberId, String type, String content, String linkUrl) {
        Long id = null;
        try {
            id = notificationMapper.nextNotificationId();
            notificationMapper.insertNotification(id, memberId, type, content, linkUrl);
        } catch (RuntimeException e) {
            id = null;
            log.warn("알림 저장 실패 member={} type={}", memberId, type, e);
        }
        NotificationDTO n =
                new NotificationDTO(id, type, content, linkUrl, "N", LocalDateTime.now());
        Deque<SseEmitter> set = emitters.get(memberId);
        if (set == null) {
            return; // 접속 중이 아니면 저장만 (다음에 목록에서 봄)
        }
        for (SseEmitter emitter : set) {
            sendNotification(memberId, emitter, n);
        }
    }

    /**
     * [추가 11/24 · 이우진 · TT-153] 같은 종류의 안 읽은 알림이 없을 때만 notifyMember (상담 메시지마다 알림이 쌓이지 않게).
     *
     * <p>확인 조회가 실패하면(DB 오류) 알림을 빼먹지 않도록 그냥 보내요.
     *
     * @return 보냈으면 true · 안 읽은 알림이 있어서 건너뛰면 false
     */
    public boolean notifyMemberUnlessUnread(
            long memberId, String type, String content, String linkUrl) {
        try {
            if (notificationMapper.countUnreadByType(memberId, type) > 0) {
                return false;
            }
        } catch (RuntimeException e) {
            log.warn("안 읽은 알림 확인 실패 member={} type={}", memberId, type, e);
        }
        notifyMember(memberId, type, content, linkUrl);
        return true;
    }

    /**
     * 활동 중인 모든 회원에게: 한 번에 저장 → 지금 접속 중인 회원에게만 보내기 (마감 안내 · TT-152).
     *
     * <p>회원마다 알림 번호가 달라서, 접속 중인 회원은 방금 저장한 자기 행을 다시 읽어 그 번호로 보내요.
     *
     * @return 저장한 행 수 (저장 실패면 0)
     */
    public int notifyAllActive(String type, String content, String linkUrl) {
        int saved;
        try {
            saved = notificationMapper.insertForActiveMembers(type, content, linkUrl);
        } catch (RuntimeException e) {
            log.warn("알림 전체 저장 실패 type={}", type, e);
            NotificationDTO live =
                    new NotificationDTO(null, type, content, linkUrl, "N", LocalDateTime.now());
            emitters.forEach(
                    (memberId, set) -> set.forEach(em -> sendNotification(memberId, em, live)));
            return 0;
        }
        emitters.forEach(
                (memberId, set) -> {
                    NotificationDTO n = findLatestQuietly(memberId, type);
                    if (n == null) {
                        return; // 탈퇴 회원 등 저장 대상이 아니었던 연결
                    }
                    for (SseEmitter emitter : set) {
                        sendNotification(memberId, emitter, n);
                    }
                });
        log.info("알림 전체 저장 type={} 행={}", type, saved);
        return saved;
    }

    /** 내 알림 목록 (종 아이콘) · 안 읽은 개수 + 최근 20개 */
    public NotificationListDTO getMyNotifications(long memberId) {
        return new NotificationListDTO(
                notificationMapper.countUnread(memberId),
                notificationMapper.findRecent(memberId, LIST_LIMIT));
    }

    /** 내 안 읽은 알림을 모두 읽음으로 (종 아이콘 목록을 열 때) */
    public void markAllRead(long memberId) {
        notificationMapper.markAllRead(memberId);
    }

    /**
     * 하트비트: 연결된 모든 화면에 25초마다 주석 줄(": ping")을 보내요.
     *
     * <p>① 화면은 신호가 60초 동안 안 오면 연결이 죽은 걸로 보고 다시 연결해요 (중간 프록시가 끊김을 안 알려 줄 때 대비). ② 서버도 보내기가 실패하면
     * 끊긴 화면을 알아채서 목록에서 빼요. 주석 줄은 이벤트가 아니라서 화면 코드는 무시해요.
     */
    @Scheduled(fixedRate = HEARTBEAT_MS, initialDelay = HEARTBEAT_MS)
    public void heartbeat() {
        emitters.forEach(
                (memberId, set) -> {
                    for (SseEmitter emitter : set) {
                        send(memberId, emitter, SseEmitter.event().comment("ping"));
                    }
                });
    }

    /** Last-Event-ID 헤더 → 알림 번호 · 없거나 숫자가 아니면 null (화면이 보낸 값이라 믿지 않고 검사) */
    static Long parseId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(lastEventId.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 끊겨 있던 동안 생긴 본인 알림을 이 연결에만 다시 보내요 (실패해도 연결은 그대로)
    private void replay(long memberId, SseEmitter emitter, Long lastId) {
        if (lastId == null) {
            return;
        }
        try {
            List<NotificationDTO> missed = notificationMapper.findAfter(memberId, lastId);
            for (NotificationDTO n : missed) {
                sendNotification(memberId, emitter, n);
            }
        } catch (RuntimeException e) {
            log.warn("놓친 알림 조회 실패 member={}", memberId, e);
        }
    }

    private NotificationDTO findLatestQuietly(long memberId, String type) {
        try {
            return notificationMapper.findLatest(memberId, type);
        } catch (RuntimeException e) {
            log.warn("알림 조회 실패 member={} type={}", memberId, type, e);
            return null;
        }
    }

    private void sendNotification(long memberId, SseEmitter emitter, NotificationDTO n) {
        SseEmitter.SseEventBuilder event =
                SseEmitter.event().name(EVENT).data(n, MediaType.APPLICATION_JSON);
        if (n.getNotificationId() != null) {
            event.id(String.valueOf(n.getNotificationId())); // 화면이 다시 연결할 때 Last-Event-ID 로 돌려줌
        }
        send(memberId, emitter, event);
    }

    private void send(long memberId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // 브라우저가 이미 닫혔으면 보내기가 실패해요 → 목록에서 빼요
            removeEmitter(memberId, emitter);
        }
    }

    // [추가 11/24 · 코드 리뷰] 연결 수가 넘으면 가장 오래된 것부터 닫음 (그 탭은 화면이 알아서 다시 연결)
    private void closeOldest(long memberId, Deque<SseEmitter> list) {
        while (list.size() > MAX_CONNECTIONS_PER_MEMBER) {
            SseEmitter oldest = list.pollFirst();
            if (oldest == null) {
                return;
            }
            log.info("알림 연결 수 초과 member={} → 가장 오래된 연결 닫음", memberId);
            try {
                oldest.complete();
            } catch (RuntimeException e) {
                log.debug("오래된 연결 닫기 실패 member={}", memberId, e);
            }
        }
    }

    /** 테스트용: 지금 열려 있는 그 회원의 연결 수 */
    int connectionCount(long memberId) {
        Deque<SseEmitter> list = emitters.get(memberId);
        return list == null ? 0 : list.size();
    }

    private void removeEmitter(long memberId, SseEmitter emitter) {
        emitters.computeIfPresent(
                memberId,
                (id, set) -> {
                    set.remove(emitter);
                    return set.isEmpty() ? null : set; // 빈 목록이면 Map에서 회원 자체를 지움
                });
    }
}
