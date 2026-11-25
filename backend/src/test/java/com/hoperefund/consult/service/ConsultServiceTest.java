package com.hoperefund.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.consult.dto.ConsultMessageDTO;
import com.hoperefund.consult.dto.ConsultRoomSummaryDTO;
import com.hoperefund.consult.mapper.ConsultMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

/** DB 없이 상담 채팅 규칙만 확인 (가짜 매퍼) · SQL 은 공용 DB 에서 따로 확인 */
class ConsultServiceTest {

    private static final ConsultUser MEMBER_A = new ConsultUser(1002L, false);
    private static final ConsultUser MEMBER_B = new ConsultUser(1003L, false);
    private static final ConsultUser ADMIN = new ConsultUser(1L, true);

    /** 방 · 메시지를 메모리에 두는 가짜 ConsultMapper */
    static class FakeConsultMapper implements ConsultMapper {
        final Map<Long, Long> roomOwners = new HashMap<>(); // roomId → memberId
        final List<ConsultMessageDTO> messages = new ArrayList<>();
        final List<ConsultRoomSummaryDTO> adminRooms = new ArrayList<>();
        final List<Long> activeAdmins = new ArrayList<>(); // findActiveAdminIds
        long roomSeq = 10;
        long messageSeq = 100;
        boolean duplicateOnInsert; // 동시에 두 번 눌린 상황 흉내
        Long receivedBeforeId;
        int receivedLimit;

        @Override
        public Long findRoomIdByMember(long memberId) {
            return roomOwners.entrySet().stream()
                    .filter(e -> e.getValue() == memberId)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Long findRoomOwner(long roomId) {
            return roomOwners.get(roomId);
        }

        @Override
        public long nextRoomId() {
            return ++roomSeq;
        }

        @Override
        public int insertRoom(long roomId, long memberId) {
            if (duplicateOnInsert) {
                roomOwners.put(99L, memberId); // 다른 요청이 먼저 만든 방
                throw new DuplicateKeyException("UQ_CONSULT_ROOM_MEMBER");
            }
            roomOwners.put(roomId, memberId);
            return 1;
        }

        @Override
        public long nextMessageId() {
            return ++messageSeq;
        }

        @Override
        public int insertMessage(long messageId, long roomId, long senderId, String content) {
            messages.add(message(messageId, roomId, senderId, content));
            return 1;
        }

        @Override
        public ConsultMessageDTO findMessage(long messageId) {
            return messages.stream()
                    .filter(m -> m.getMessageId() == messageId)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<ConsultMessageDTO> findMessagesBefore(long roomId, Long beforeId, int limit) {
            receivedBeforeId = beforeId;
            receivedLimit = limit;
            // 실제 SQL 처럼 새것부터
            return messages.stream()
                    .filter(m -> m.getRoomId() == roomId)
                    .filter(m -> beforeId == null || m.getMessageId() < beforeId)
                    .sorted((a, b) -> Long.compare(b.getMessageId(), a.getMessageId()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<ConsultRoomSummaryDTO> findRoomsForAdmin(int limit) {
            return adminRooms;
        }

        @Override
        public List<Long> findActiveAdminIds() {
            return activeAdmins;
        }
    }

    private FakeConsultMapper mapper;
    private ConsultService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeConsultMapper();
        service = new ConsultService(mapper);
    }

    private static ConsultMessageDTO message(
            long messageId, long roomId, long senderId, String content) {
        ConsultMessageDTO message = new ConsultMessageDTO();
        message.setMessageId(messageId);
        message.setRoomId(roomId);
        message.setSenderId(senderId);
        message.setSenderRole(senderId == ADMIN.memberId() ? "ADMIN" : "USER");
        message.setContent(content);
        message.setCreatedAt("2026-11-24T10:00:00");
        return message;
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((BusinessException) e).getStatus();
    }

    @Test
    @DisplayName("상담 요청: 방이 없으면 새로 만들고, 다시 누르면 같은 방을 돌려준다")
    void openRoomCreatesOnceAndReturnsSameRoom() {
        long first = service.openRoom(MEMBER_A);
        long second = service.openRoom(MEMBER_A);

        assertThat(first).isEqualTo(11L);
        assertThat(second).isEqualTo(first);
        assertThat(mapper.roomOwners).containsEntry(11L, MEMBER_A.memberId()).hasSize(1);
    }

    @Test
    @DisplayName("상담 요청: 거의 동시에 두 번 들어와 UNIQUE 에 걸리면 먼저 만든 방을 돌려준다")
    void openRoomReturnsExistingRoomOnDuplicateKey() {
        mapper.duplicateOnInsert = true;

        assertThat(service.openRoom(MEMBER_A)).isEqualTo(99L);
    }

    @Test
    @DisplayName("상담 요청: 관리자는 자기 방을 만들 수 없다 (403)")
    void adminCannotOpenRoom() {
        assertThatThrownBy(() -> service.openRoom(ADMIN))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(mapper.roomOwners).isEmpty();
    }

    @Test
    @DisplayName("방에 들어올 수 있는 사람: 방 주인 회원 · 관리자 (다른 회원 · 없는 방은 안 됨)")
    void canAccessOnlyOwnerOrAdmin() {
        mapper.roomOwners.put(11L, MEMBER_A.memberId());

        assertThat(service.canAccess(MEMBER_A, 11L)).isTrue();
        assertThat(service.canAccess(ADMIN, 11L)).isTrue();
        assertThat(service.canAccess(MEMBER_B, 11L)).isFalse();
        assertThat(service.canAccess(ADMIN, 999L)).isFalse();
    }

    @Test
    @DisplayName("지난 메시지: 오래된 순으로 돌려주고, 내가 보낸 것만 mine = true")
    void getMessagesReturnsOldestFirstWithMineFlag() {
        mapper.roomOwners.put(11L, MEMBER_A.memberId());
        mapper.messages.add(message(101, 11, MEMBER_A.memberId(), "질문이 있어요"));
        mapper.messages.add(message(102, 11, ADMIN.memberId(), "네, 말씀하세요"));

        List<ConsultMessageDTO> result = service.getMessages(MEMBER_A, 11L, null);

        assertThat(result).extracting(ConsultMessageDTO::getMessageId).containsExactly(101L, 102L);
        assertThat(result).extracting(ConsultMessageDTO::getMine).containsExactly(true, false);
        assertThat(mapper.receivedLimit).isEqualTo(ConsultService.PAGE_SIZE);
    }

    @Test
    @DisplayName("지난 메시지: beforeId 를 그대로 넘겨 그보다 오래된 것만 받는다")
    void getMessagesPassesBeforeId() {
        mapper.roomOwners.put(11L, MEMBER_A.memberId());
        mapper.messages.add(message(101, 11, MEMBER_A.memberId(), "첫 질문"));
        mapper.messages.add(message(102, 11, MEMBER_A.memberId(), "두 번째"));

        List<ConsultMessageDTO> result = service.getMessages(MEMBER_A, 11L, 102L);

        assertThat(mapper.receivedBeforeId).isEqualTo(102L);
        assertThat(result).extracting(ConsultMessageDTO::getMessageId).containsExactly(101L);
    }

    @Test
    @DisplayName("지난 메시지: 남의 방 · 없는 방은 404 (방이 있는지도 숨김)")
    void getMessagesOfOthersRoomIsNotFound() {
        mapper.roomOwners.put(11L, MEMBER_A.memberId());

        assertThatThrownBy(() -> service.getMessages(MEMBER_B, 11L, null))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.getMessages(MEMBER_A, 999L, null))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("지난 메시지: beforeId 가 0 이하면 400")
    void getMessagesRejectsNonPositiveBeforeId() {
        mapper.roomOwners.put(11L, MEMBER_A.memberId());

        assertThatThrownBy(() -> service.getMessages(MEMBER_A, 11L, 0L))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("보내기: 저장하고, 보낸 사람은 로그인한 사람으로 남는다 (관리자는 아무 방에나)")
    void sendSavesWithLoginUserAsSender() {
        mapper.roomOwners.put(11L, MEMBER_A.memberId());

        ConsultMessageDTO fromMember = service.send(MEMBER_A, 11L, "카드 공제가 궁금해요");
        ConsultMessageDTO fromAdmin = service.send(ADMIN, 11L, "확인해 드릴게요");

        assertThat(fromMember.getSenderId()).isEqualTo(MEMBER_A.memberId());
        assertThat(fromAdmin.getSenderId()).isEqualTo(ADMIN.memberId());
        assertThat(mapper.messages).hasSize(2);
    }

    @Test
    @DisplayName("보내기: 빈 메시지 · 공백만 · 1000자 초과는 400 · 저장 안 함")
    void sendRejectsBlankAndTooLong() {
        mapper.roomOwners.put(11L, MEMBER_A.memberId());

        assertThatThrownBy(() -> service.send(MEMBER_A, 11L, null))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.send(MEMBER_A, 11L, "   "))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.send(MEMBER_A, 11L, "가".repeat(1001)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(mapper.messages).isEmpty();
    }

    @Test
    @DisplayName("보내기: 1000자(이모지 포함 글자 수)는 보낼 수 있다")
    void sendAllowsExactlyMaxLength() {
        mapper.roomOwners.put(11L, MEMBER_A.memberId());
        String content = "😀".repeat(ConsultService.MAX_CONTENT_LENGTH); // 자바 길이는 2000 · 글자는 1000

        assertThat(service.send(MEMBER_A, 11L, content)).isNotNull();
    }

    @Test
    @DisplayName("보내기: 남의 방에는 404 · 저장 안 함")
    void sendToOthersRoomIsNotFound() {
        mapper.roomOwners.put(11L, MEMBER_A.memberId());

        assertThatThrownBy(() -> service.send(MEMBER_B, 11L, "안녕하세요"))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(mapper.messages).isEmpty();
    }

    @Test
    @DisplayName("관리자 방 목록: 회원 아이디는 일부만 보인다")
    void adminRoomsMaskLoginId() {
        ConsultRoomSummaryDTO room = new ConsultRoomSummaryDTO();
        room.setRoomId(11L);
        room.setCustomer("test1234");
        mapper.adminRooms.add(room);

        assertThat(service.getRoomsForAdmin())
                .extracting(ConsultRoomSummaryDTO::getCustomer)
                .containsExactly("test****");
    }

    @Test
    @DisplayName("아이디 가림: 앞 절반(최대 4글자)만 보이고 나머지는 ****")
    void maskLoginIdKeepsAtMostHalf() {
        assertThat(ConsultService.maskLoginId("test1234")).isEqualTo("test****");
        assertThat(ConsultService.maskLoginId("hope_user_01")).isEqualTo("hope****");
        assertThat(ConsultService.maskLoginId("abc")).isEqualTo("a****");
        assertThat(ConsultService.maskLoginId(null)).isEmpty();
    }
}
