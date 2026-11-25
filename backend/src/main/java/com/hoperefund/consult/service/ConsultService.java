package com.hoperefund.consult.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.consult.dto.ConsultMessageDTO;
import com.hoperefund.consult.dto.ConsultRoomSummaryDTO;
import com.hoperefund.consult.mapper.ConsultMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * [추가 11/24 · 이우진 · TT-153] 관리자 상담 채팅 (F-07-03)
 *
 * <ul>
 *   <li>방은 회원당 1개 · 관리자는 여러 명이 아무 방에나 답함 (참여자 테이블 없음 · 보낸 사람은 메시지에 남음)
 *   <li>들어올 수 있는 사람: 방 주인 회원 또는 관리자 · 아니면 404 (남의 방이 있는지도 숨김)
 *   <li>메시지 전달(STOMP)은 ConsultMessageController 가 함 → 여기는 DB 만 다룸 (WebSocket 설정과 서로 물리지 않게)
 * </ul>
 */
@Service
public class ConsultService {

    static final int MAX_CONTENT_LENGTH = 1000; // CONSULT_MESSAGE.CONTENT VARCHAR2(1000 CHAR)
    static final int PAGE_SIZE = 30; // 지난 메시지 한 번에
    static final int ADMIN_ROOM_LIMIT = 100; // 관리자 방 목록 최대

    private final ConsultMapper consultMapper;

    public ConsultService(ConsultMapper consultMapper) {
        this.consultMapper = consultMapper;
    }

    /** ① 상담 요청: 내 방이 있으면 그 방, 없으면 새로 만듦 (관리자는 자기 방이 없음 → 403) */
    public long openRoom(ConsultUser user) {
        if (user.admin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자는 상담 요청을 만들 수 없어요");
        }
        Long roomId = consultMapper.findRoomIdByMember(user.memberId());
        if (roomId != null) {
            return roomId;
        }
        long newRoomId = consultMapper.nextRoomId();
        try {
            consultMapper.insertRoom(newRoomId, user.memberId());
            return newRoomId;
        } catch (DuplicateKeyException e) {
            // 버튼을 두 번 눌러 거의 동시에 들어오면 UNIQUE 에 걸림 → 먼저 만든 방을 돌려줌
            Long existing = consultMapper.findRoomIdByMember(user.memberId());
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    /** ③ · ⑤ 방에 들어올 수 있는지 (방 주인 회원 또는 관리자) · 없는 방이면 false */
    public boolean canAccess(ConsultUser user, long roomId) {
        Long owner = consultMapper.findRoomOwner(roomId);
        if (owner == null) {
            return false;
        }
        return user.admin() || owner == user.memberId();
    }

    /** ⑤ 지난 메시지: beforeId 보다 오래된 것 30개 · 오래된 순으로 · 내가 보낸 것은 mine = true */
    public List<ConsultMessageDTO> getMessages(ConsultUser user, long roomId, Long beforeId) {
        if (beforeId != null && beforeId <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VALIDATION", "beforeId는 1 이상이어야 해요");
        }
        checkAccess(user, roomId);
        List<ConsultMessageDTO> messages =
                new ArrayList<>(consultMapper.findMessagesBefore(roomId, beforeId, PAGE_SIZE));
        Collections.reverse(messages); // SQL 은 새것부터 → 화면은 위에서 아래로 오래된 순
        for (ConsultMessageDTO message : messages) {
            message.setMine(
                    message.getSenderId() != null && message.getSenderId() == user.memberId());
        }
        return messages;
    }

    /** ④ 메시지 저장: 1~1000자 확인 → 방 권한 확인 → 저장 → 저장된 1건 (전달은 컨트롤러가) */
    public ConsultMessageDTO send(ConsultUser user, long roomId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION", "메시지를 입력해 주세요");
        }
        // DB 는 글자 수(CHAR) 기준 → 이모지처럼 두 칸짜리 글자도 1자로 셈
        if (content.codePointCount(0, content.length()) > MAX_CONTENT_LENGTH) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION",
                    "메시지는 " + MAX_CONTENT_LENGTH + "자까지 보낼 수 있어요");
        }
        checkAccess(user, roomId);
        long messageId = consultMapper.nextMessageId();
        consultMapper.insertMessage(messageId, roomId, user.memberId(), content);
        return consultMapper.findMessage(messageId);
    }

    /** 관리자 방 목록: 최근 메시지 순 · 아이디 일부 가림 */
    public List<ConsultRoomSummaryDTO> getRoomsForAdmin() {
        List<ConsultRoomSummaryDTO> rooms = consultMapper.findRoomsForAdmin(ADMIN_ROOM_LIMIT);
        for (ConsultRoomSummaryDTO room : rooms) {
            room.setCustomer(maskLoginId(room.getCustomer()));
        }
        return rooms;
    }

    private void checkAccess(ConsultUser user, long roomId) {
        if (!canAccess(user, roomId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "상담 방을 찾을 수 없어요");
        }
    }

    // test1234 → test**** · 앞 절반(최대 4글자)만 보임 · 관리자 회원 관리 화면의 이메일 가림과 같은 생각
    static String maskLoginId(String loginId) {
        if (loginId == null || loginId.isEmpty()) {
            return "";
        }
        int visible = Math.min(4, Math.max(1, loginId.length() / 2));
        return loginId.substring(0, visible) + "****";
    }
}
