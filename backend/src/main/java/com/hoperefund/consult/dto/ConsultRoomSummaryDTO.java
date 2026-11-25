package com.hoperefund.consult.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** [추가 11/24 · 이우진 · TT-153] 관리자 상담 방 목록 1행 (최근 메시지 순) */
@Getter
@Setter
@NoArgsConstructor
public class ConsultRoomSummaryDTO {

    private Long roomId; // 방번호
    private String customer; // 회원 아이디 (SQL 은 그대로 · ConsultService 가 일부 가림 → test****)
    private String createdAt; // 방 만든 시각
    private String lastMessageAt; // 마지막 메시지 시각 (메시지가 없으면 null)
}
