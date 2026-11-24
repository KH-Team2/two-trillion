package com.hoperefund.consult.mapper;

import com.hoperefund.consult.dto.ConsultMessageDTO;
import com.hoperefund.consult.dto.ConsultRoomSummaryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * [추가 11/24 · 이우진 · TT-153] 상담 방 · 메시지 (CONSULT_ROOM · CONSULT_MESSAGE · ERD v4.3)
 *
 * <p>SQL 은 resources/mapper/consult/ConsultMapper.xml · 값은 모두 #{} 로만 넣음
 */
@Mapper
public interface ConsultMapper {

    /** 회원의 상담 방 번호 (없으면 null · 회원당 1개 UQ_CONSULT_ROOM_MEMBER) */
    Long findRoomIdByMember(@Param("memberId") long memberId);

    /** 방 주인 회원번호 (없는 방이면 null) */
    Long findRoomOwner(@Param("roomId") long roomId);

    long nextRoomId();

    int insertRoom(@Param("roomId") long roomId, @Param("memberId") long memberId);

    long nextMessageId();

    int insertMessage(
            @Param("messageId") long messageId,
            @Param("roomId") long roomId,
            @Param("senderId") long senderId,
            @Param("content") String content);

    /** 메시지 1건 (보낸 사람 역할 · 문자열 시각 포함) */
    ConsultMessageDTO findMessage(@Param("messageId") long messageId);

    /** beforeId 보다 오래된 메시지 limit 개 · 새것부터 (beforeId 가 null 이면 가장 최근부터) */
    List<ConsultMessageDTO> findMessagesBefore(
            @Param("roomId") long roomId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    /** 관리자 방 목록 · 활동 중인 회원 방만 · 최근 메시지 순 (메시지 없는 방은 뒤) · limit 개 */
    List<ConsultRoomSummaryDTO> findRoomsForAdmin(@Param("limit") int limit);
}
