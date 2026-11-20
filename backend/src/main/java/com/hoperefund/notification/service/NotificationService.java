package com.hoperefund.notification.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 알림 (2차 미션 · TT-150).
 *
 * <p>회원 번호 → 열려 있는 연결 목록을 메모리에 보관해요. 같은 회원이 탭을 여러 개 열면 연결도 여러 개예요. 서버를 재시작하면 목록이 사라지고,
 * 화면이 다시 연결해요.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final long TIMEOUT_MS = 30L * 60 * 1000; // 30분 지나면 연결을 닫음 → 화면이 다시 연결
    private static final long HEARTBEAT_MS = 25_000; // 할 말이 없어도 25초마다 신호 (화면은 60초 조용하면 다시 연결)

    // 여러 요청이 동시에 넣고 빼도 안전한 Map · Set
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // 구독: 연결을 하나 만들어 목록에 넣고 돌려줘요
    public SseEmitter subscribe(long memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(memberId, id -> ConcurrentHashMap.newKeySet()).add(emitter);

        // 연결이 끝나면(정상 종료 · 시간 초과 · 오류) 목록에서 빼요 → 메모리가 계속 늘지 않게
        Runnable remove = () -> removeEmitter(memberId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());

        // 연결되자마자 한 번 보내야 브라우저가 「연결됨」을 바로 알아요
        send(memberId, emitter, "connected", "ok");
        log.info("알림 연결 member={} 연결 수={}", memberId, emitters.get(memberId).size());
        return emitter;
    }

    // 그 회원의 모든 연결(탭)에 보내요 · 연결이 없으면 아무것도 안 함
    public void sendToMember(long memberId, String eventName, Object data) {
        Set<SseEmitter> set = emitters.get(memberId);
        if (set == null) {
            return;
        }
        for (SseEmitter emitter : set) {
            send(memberId, emitter, eventName, data);
        }
    }

    // [추가 11/20 · 이우진 · TT-152] 접속 중인 모든 회원에게 보내요 (마감 안내용)
    // 알림 DB가 아직 없어서, 지금 접속해 있지 않은 회원은 받지 못해요
    public void sendToAll(String eventName, Object data) {
        emitters.forEach(
                (memberId, set) -> {
                    for (SseEmitter emitter : set) {
                        send(memberId, emitter, eventName, data);
                    }
                });
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
                        try {
                            emitter.send(SseEmitter.event().comment("ping"));
                        } catch (IOException | IllegalStateException e) {
                            removeEmitter(memberId, emitter);
                        }
                    }
                });
    }

    private void send(long memberId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            // 브라우저가 이미 닫혔으면 보내기가 실패해요 → 목록에서 빼요
            removeEmitter(memberId, emitter);
        }
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
