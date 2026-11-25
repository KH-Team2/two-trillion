package com.hoperefund.notification.service;

import com.hoperefund.notification.dto.NotificationDTO;
import com.hoperefund.notification.mapper.NotificationMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** DB 없이 NotificationMapper 를 흉내 내는 가짜 (메모리 목록) · 다른 패키지 테스트에서도 써요 */
public class FakeNotificationMapper implements NotificationMapper {

    /** 저장된 행 (회원 번호 포함) */
    public record Row(long memberId, NotificationDTO n) {}

    public final List<Row> rows = new ArrayList<>();
    public Set<Long> activeMembers = Set.of(); // insertForActiveMembers 대상
    public boolean fail; // true 면 저장 · 조회가 DB 오류처럼 실패
    private long seq;

    @Override
    public long nextNotificationId() {
        check();
        return ++seq;
    }

    @Override
    public void insertNotification(
            long notificationId, long memberId, String notiType, String content, String linkUrl) {
        check();
        rows.add(
                new Row(
                        memberId,
                        new NotificationDTO(
                                notificationId,
                                notiType,
                                content,
                                linkUrl,
                                "N",
                                LocalDateTime.now())));
    }

    @Override
    public int insertForActiveMembers(String notiType, String content, String linkUrl) {
        check();
        activeMembers.stream()
                .sorted()
                .forEach(m -> insertNotification(++seq, m, notiType, content, linkUrl));
        return activeMembers.size();
    }

    @Override
    public NotificationDTO findLatest(long memberId, String notiType) {
        check();
        return mine(memberId).stream()
                .filter(n -> n.getNotiType().equals(notiType))
                .max(Comparator.comparing(NotificationDTO::getNotificationId))
                .orElse(null);
    }

    @Override
    public List<NotificationDTO> findRecent(long memberId, int limit) {
        check();
        return mine(memberId).stream()
                .sorted(Comparator.comparing(NotificationDTO::getNotificationId).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public long countUnread(long memberId) {
        check();
        return mine(memberId).stream().filter(n -> "N".equals(n.getReadYn())).count();
    }

    @Override
    public long countUnreadByType(long memberId, String notiType) {
        check();
        return mine(memberId).stream()
                .filter(n -> n.getNotiType().equals(notiType) && "N".equals(n.getReadYn()))
                .count();
    }

    @Override
    public List<NotificationDTO> findAfter(long memberId, long lastId) {
        check();
        return mine(memberId).stream()
                .filter(n -> n.getNotificationId() > lastId)
                .sorted(Comparator.comparing(NotificationDTO::getNotificationId))
                .limit(50)
                .toList();
    }

    @Override
    public int markAllRead(long memberId) {
        check();
        List<NotificationDTO> unread =
                mine(memberId).stream().filter(n -> "N".equals(n.getReadYn())).toList();
        unread.forEach(n -> n.setReadYn("Y"));
        return unread.size();
    }

    private List<NotificationDTO> mine(long memberId) {
        return rows.stream().filter(r -> r.memberId() == memberId).map(Row::n).toList();
    }

    private void check() {
        if (fail) {
            throw new IllegalStateException("가짜 DB 오류");
        }
    }
}
