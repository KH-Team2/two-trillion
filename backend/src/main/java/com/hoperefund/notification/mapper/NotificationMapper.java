package com.hoperefund.notification.mapper;

import com.hoperefund.notification.dto.NotificationDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 알림 저장 · 조회 (NOTIFICATION · ERD v4.2 · TT-158) · SQL 은 resources/mapper/notification/NotificationMapper.xml.
 *
 * <p>조회 · 읽음 처리는 모두 MEMBER_ID 조건을 붙여요 (본인 알림만 · 회원 번호는 토큰에서).
 */
@Mapper
public interface NotificationMapper {

    /** 새 알림 번호 (SEQ_NOTIFICATION) */
    long nextNotificationId();

    /** 한 회원에게 알림 한 건 저장 (READ_YN · CREATED_AT 은 DB 기본값) */
    void insertNotification(
            @Param("notificationId") long notificationId,
            @Param("memberId") long memberId,
            @Param("notiType") String notiType,
            @Param("content") String content,
            @Param("linkUrl") String linkUrl);

    /** 활동 중(ACTIVE)인 모든 회원에게 같은 알림 저장 (마감 안내) · 저장한 행 수 */
    int insertForActiveMembers(
            @Param("notiType") String notiType,
            @Param("content") String content,
            @Param("linkUrl") String linkUrl);

    /** 그 회원의 그 종류 알림 중 가장 최근 것 · 없으면 null (방금 전체 저장한 마감 안내를 접속 중인 회원에게 보낼 때) */
    NotificationDTO findLatest(
            @Param("memberId") long memberId, @Param("notiType") String notiType);

    /** 최근 알림 (새것부터 · limit 개) */
    List<NotificationDTO> findRecent(@Param("memberId") long memberId, @Param("limit") int limit);

    /** 안 읽은 알림 개수 */
    long countUnread(@Param("memberId") long memberId);

    /** lastId 보다 뒤에 생긴 알림 (오래된 것부터 · 최대 50개) · 다시 연결할 때 놓친 알림 */
    List<NotificationDTO> findAfter(@Param("memberId") long memberId, @Param("lastId") long lastId);

    /** 안 읽은 알림을 모두 읽음으로 (READ_AT = 지금) · 바꾼 행 수 */
    int markAllRead(@Param("memberId") long memberId);
}
