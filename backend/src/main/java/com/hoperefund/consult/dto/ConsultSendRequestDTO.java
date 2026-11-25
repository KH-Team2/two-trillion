package com.hoperefund.consult.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [추가 11/24 · 이우진 · TT-153] STOMP SEND /pub/consult/{roomId} 본문 {"content": "..."}
 *
 * <p>보낸 사람 · 방번호는 받지 않음 (보낸 사람은 로그인 정보에서 · 방번호는 주소에서)
 */
@Getter
@Setter
@NoArgsConstructor
public class ConsultSendRequestDTO {

    private String content;
}
