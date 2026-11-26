package com.hoperefund.notification.dto;

import java.util.List;

/**
 * 내 알림 목록 응답 (TT-158 · GET /api/notifications) · 종 아이콘 배지 숫자와 목록을 한 번에 줘요.
 *
 * @param unreadCount 안 읽은 알림 개수 (목록에 안 보이는 오래된 것까지 포함)
 * @param items 최근 알림 (새것부터 · 최대 20개)
 */
public record NotificationListDTO(long unreadCount, List<NotificationDTO> items) {}
