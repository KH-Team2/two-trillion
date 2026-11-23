package com.hoperefund.notification.controller;

import com.hoperefund.notification.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 알림 연결 (2차 미션 · TT-150).
 *
 * <p>GET /api/notifications/stream · 로그인한 회원만 (다른 /api/** 와 같은 JWT 검사) · 회원 번호는 토큰에서만.
 * 응답은 공통 봉투(ApiResponse)가 아니라 text/event-stream 이에요 → 연결을 열어 두고 이벤트를 계속 흘려보내요.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Long memberId) {
        return notificationService.subscribe(memberId);
    }
}
