package com.hoperefund.consult.config;

import com.hoperefund.common.service.TokenService;
import com.hoperefund.consult.service.ConsultService;
import com.hoperefund.consult.service.ConsultUser;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * [추가 11/24 · 이우진 · TT-153] STOMP 프레임 검사 (교안 보안 포인트)
 *
 * <ul>
 *   <li>CONNECT: CONNECT 헤더의 Authorization: Bearer 토큰을 REST 와 같은 TokenService 로 확인 → 로그인 정보를 연결에 붙임 ·
 *       없거나 틀리면 막음 (STOMP ERROR 후 연결 종료)
 *   <li>SUBSCRIBE: /sub/consult/{roomId} 는 방 주인 회원 또는 관리자만 · /user/queue/errors 는 누구나(로그인한 사람)
 *       · 그 밖의 주소는 막음 (방 번호만 바꿔 남의 대화를 엿보는 것 방지)
 *   <li>SEND: 로그인 정보가 없으면 막음 · 방 권한은 ConsultService.send 에서 다시 확인
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String ROOM_TOPIC_PREFIX = "/sub/consult/";
    static final String ERROR_QUEUE = "/user/queue/errors";

    private static final String BEARER_PREFIX = TokenService.TOKEN_TYPE + " ";

    private final TokenService tokenService;
    private final ConsultService consultService;

    public StompAuthChannelInterceptor(TokenService tokenService, ConsultService consultService) {
        this.tokenService = tokenService;
        this.consultService = consultService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(
                    authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
        } else if (command == StompCommand.SUBSCRIBE) {
            checkSubscribe(loginUser(accessor.getUser()), accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            loginUser(accessor.getUser());
        }
        return message;
    }

    // CONNECT 헤더 토큰 → REST JWT 필터와 같은 모양의 로그인 정보 (principal = 회원번호 · 권한 = ROLE_USER / ROLE_ADMIN)
    private UsernamePasswordAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("로그인이 필요해요");
        }
        return tokenService
                .verify(header.substring(BEARER_PREFIX.length()))
                .map(
                        info ->
                                new UsernamePasswordAuthenticationToken(
                                        info.memberId(),
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + info.role()))))
                .orElseThrow(() -> new AccessDeniedException("로그인이 필요해요"));
    }

    private ConsultUser loginUser(Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("로그인이 필요해요");
        }
        return ConsultUser.from(principal);
    }

    private void checkSubscribe(ConsultUser user, String destination) {
        if (ERROR_QUEUE.equals(destination)) {
            return;
        }
        Long roomId = roomIdOf(destination);
        if (roomId == null || !consultService.canAccess(user, roomId)) {
            throw new AccessDeniedException("구독할 수 없는 방이에요");
        }
    }

    // /sub/consult/12 → 12 · 형식이 다르면 null
    static Long roomIdOf(String destination) {
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return null;
        }
        String value = destination.substring(ROOM_TOPIC_PREFIX.length());
        if (value.isEmpty()
                || value.length() > 18
                || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return null;
        }
        long roomId = Long.parseLong(value);
        return roomId > 0 ? roomId : null;
    }
}
