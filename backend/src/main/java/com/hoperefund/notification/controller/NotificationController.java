package com.hoperefund.notification.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.notification.dto.NotificationListDTO;
import com.hoperefund.notification.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 알림 API (2차 미션 · TT-150 · TT-158) · 로그인한 회원만 (다른 /api/** 와 같은 JWT 검사) · 회원 번호는 토큰에서만.
 *
 * <p>stream 은 공통 봉투(ApiResponse)가 아니라 text/event-stream 이에요 → 연결을 열어 두고 이벤트를 계속 흘려보내요. 목록 · 읽음은
 * 다른 API 처럼 공통 봉투예요.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 실시간 연결 · [수정 11/23 · TT-158] 다시 연결할 때 Last-Event-ID(마지막으로 받은 알림 번호)를 받으면 그 뒤 알림을 다시 보내요 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @AuthenticationPrincipal Long memberId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return notificationService.subscribe(memberId, lastEventId);
    }

    /** 내 알림 목록: GET /api/notifications · 안 읽은 개수 + 최근 20개 (새것부터) */
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListDTO>> list(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getMyNotifications(memberId)));
    }

    /** 모두 읽음: PUT /api/notifications/read · 본문 없음 · 응답 data 는 null */
    @PutMapping("/read")
    public ResponseEntity<ApiResponse<Void>> readAll(@AuthenticationPrincipal Long memberId) {
        notificationService.markAllRead(memberId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
