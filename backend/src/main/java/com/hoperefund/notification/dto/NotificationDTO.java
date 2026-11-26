package com.hoperefund.notification.dto;

import java.time.LocalDateTime;

/**
 * 알림 한 건 (NOTIFICATION 한 행 · TT-158) · 알림 목록 API 응답과 SSE notification 이벤트 data 로 같이 써요.
 *
 * <p>받는 회원 번호(MEMBER_ID)는 넣지 않아요 → 본인 것만 조회하니 필요 없고, 밖으로 내보낼 이유도 없어요. 문구(content)에는 금액 같은
 * 금융 정보를 넣지 않아요.
 */
public class NotificationDTO {
    private Long notificationId; // 알림 번호 (SSE id · 다시 연결할 때 Last-Event-ID 기준) · 저장 실패 시 null
    private String notiType; // CALCULATION · DEADLINE · CHAT
    private String content; // 화면에 보이는 문구
    private String linkUrl; // 눌렀을 때 갈 화면 (dashboard · input 등 · null 이면 이동 없음)
    private String readYn; // Y · N
    private LocalDateTime createdAt; // 만든 시각

    public NotificationDTO() {}

    public NotificationDTO(
            Long notificationId,
            String notiType,
            String content,
            String linkUrl,
            String readYn,
            LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.notiType = notiType;
        this.content = content;
        this.linkUrl = linkUrl;
        this.readYn = readYn;
        this.createdAt = createdAt;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public String getNotiType() {
        return notiType;
    }

    public void setNotiType(String notiType) {
        this.notiType = notiType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getReadYn() {
        return readYn;
    }

    public void setReadYn(String readYn) {
        this.readYn = readYn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
