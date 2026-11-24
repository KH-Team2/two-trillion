package com.hoperefund.consult.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [추가 11/24 · 이우진 · TT-153] 상담 메시지 1건 (CONSULT_MESSAGE + 보낸 사람 역할)
 *
 * <p>REST 지난 메시지 응답 · STOMP /sub/consult/{roomId} 로 보내는 메시지 모두 이 모양. 보낸 사람 이름은 없음 (실명 미수집 ·
 * 화면은 senderRole 로 「상담사」 · 「고객님」 표시).
 */
@Getter
@Setter
@NoArgsConstructor
public class ConsultMessageDTO {

    private Long messageId; // 메시지번호 (지난 메시지 beforeId 기준)
    private Long roomId; // 방번호
    private Long senderId; // 보낸 사람 회원번호 (화면은 내 회원번호와 비교해 「나」 표시)
    private String senderRole; // USER / ADMIN
    private String content; // 메시지 내용 (화면은 글자 그대로만 그림)
    private String createdAt; // 보낸 시각 (SQL 에서 문자열로 · STOMP 변환기가 날짜를 배열로 내보내지 않게)

    // REST 지난 메시지에서만 채움 (내가 보낸 것인지) · 방 전체에 보내는 STOMP 메시지에는 없음
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean mine;
}
