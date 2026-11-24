package com.hoperefund.consult.controller;

import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.consult.dto.ConsultMessageDTO;
import com.hoperefund.consult.dto.ConsultRoomResponseDTO;
import com.hoperefund.consult.dto.ConsultRoomSummaryDTO;
import com.hoperefund.consult.service.ConsultService;
import com.hoperefund.consult.service.ConsultUser;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [추가 11/24 · 이우진 · TT-153] 상담 채팅 REST API (API 명세서 CONSULT-01 ~ 03)
 *
 * <p>회원번호 · 관리자 여부는 로그인 토큰에서만 (JWT 필터가 넣은 Authentication). /api/admin/** 는 SecurityConfig 가
 * ADMIN 만 통과시킴.
 */
@RestController
public class ConsultController {

    private final ConsultService consultService;

    public ConsultController(ConsultService consultService) {
        this.consultService = consultService;
    }

    /** CONSULT-01 상담 요청: 내 방이 있으면 그 방, 없으면 새로 만듦 */
    @PostMapping("/api/consult/rooms")
    public ResponseEntity<ApiResponse<ConsultRoomResponseDTO>> openRoom(
            Authentication authentication) {
        long roomId = consultService.openRoom(ConsultUser.from(authentication));
        return ResponseEntity.ok(ApiResponse.ok(new ConsultRoomResponseDTO(roomId)));
    }

    /** CONSULT-02 지난 메시지: GET /api/consult/rooms/12/messages?beforeId=105 (beforeId 없으면 가장 최근 30개) */
    @GetMapping("/api/consult/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<List<ConsultMessageDTO>>> getMessages(
            Authentication authentication,
            @PathVariable("roomId") long roomId,
            @RequestParam(value = "beforeId", required = false) Long beforeId) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        consultService.getMessages(
                                ConsultUser.from(authentication), roomId, beforeId)));
    }

    /** CONSULT-03 관리자 상담 방 목록 (최근 메시지 순 · 아이디 일부 가림) */
    @GetMapping("/api/admin/consult/rooms")
    public ResponseEntity<ApiResponse<List<ConsultRoomSummaryDTO>>> getRoomsForAdmin() {
        return ResponseEntity.ok(ApiResponse.ok(consultService.getRoomsForAdmin()));
    }
}
