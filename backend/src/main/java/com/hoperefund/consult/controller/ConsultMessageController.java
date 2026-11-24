package com.hoperefund.consult.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.consult.dto.ConsultMessageDTO;
import com.hoperefund.consult.dto.ConsultSendRequestDTO;
import com.hoperefund.consult.service.ConsultNotifier;
import com.hoperefund.consult.service.ConsultService;
import com.hoperefund.consult.service.ConsultUser;
import java.security.Principal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * [추가 11/24 · 이우진 · TT-153] 상담 메시지 보내기 (STOMP · API 명세서 WS-03)
 *
 * <p>SEND /pub/consult/{roomId} {"content": "..."} → 저장 → /sub/consult/{roomId} 구독자 모두에게 전달. 실패하면 보낸
 * 사람에게만 /user/queue/errors 로 {code, message}. 로그에는 회원번호 · 방번호만 (메시지 내용 X).
 */
@Slf4j
@Controller
public class ConsultMessageController {

    static final String ROOM_TOPIC_PREFIX = "/sub/consult/";

    private final ConsultService consultService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConsultNotifier consultNotifier;

    public ConsultMessageController(
            ConsultService consultService,
            SimpMessagingTemplate messagingTemplate,
            ConsultNotifier consultNotifier) {
        this.consultService = consultService;
        this.messagingTemplate = messagingTemplate;
        this.consultNotifier = consultNotifier;
    }

    @MessageMapping("/consult/{roomId}")
    public void send(
            @DestinationVariable("roomId") long roomId,
            @Payload ConsultSendRequestDTO request,
            Principal principal) {
        ConsultUser user = ConsultUser.from(principal); // 보낸 사람은 연결할 때 확인한 토큰에서만
        ConsultMessageDTO saved =
                consultService.send(user, roomId, request == null ? null : request.getContent());
        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomId, saved);
        // [추가 11/24 · 6단계] 상대가 이 방을 보고 있지 않으면 종 알림 (회원 → 관리자 전원 · 관리자 → 그 회원)
        consultNotifier.afterSend(user, roomId);
    }

    /** 업무 규칙 오류 (빈 메시지 · 1000자 초과 · 남의 방) → 보낸 사람에게만 */
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiResponse.ErrorInfo handleBusiness(BusinessException e) {
        return new ApiResponse.ErrorInfo(e.getCode(), e.getMessage(), null);
    }

    /** 그 밖의 오류 → 문구는 숨기고 서버 로그에만 (메시지 내용은 남기지 않음) */
    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiResponse.ErrorInfo handleUnexpected(Exception e, Principal principal) {
        log.error(
                "상담 메시지 처리 실패 · user={} · {}",
                principal == null ? null : principal.getName(),
                e.getClass().getSimpleName());
        return new ApiResponse.ErrorInfo("SERVER_ERROR", "메시지를 보내지 못했어요. 잠시 후 다시 시도해 주세요", null);
    }
}
