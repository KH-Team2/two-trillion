package com.hoperefund.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hoperefund.common.service.TokenService;
import com.hoperefund.consult.service.ConsultService;
import com.hoperefund.consult.service.ConsultUser;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** STOMP 프레임 검사 (CONNECT 토큰 · SUBSCRIBE 방 권한) · 실제 TokenService + 가짜 방 권한 */
class StompAuthChannelInterceptorTest {

    private static final String SECRET = "test-jwt-secret-must-be-at-least-32-bytes";

    private TokenService tokenService;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(SECRET, Duration.ofHours(2));
        // 방 11 은 회원 1002 의 방 · 관리자는 아무 방이나 (DB 없이 규칙만)
        ConsultService consultService =
                new ConsultService(null) {
                    @Override
                    public boolean canAccess(ConsultUser user, long roomId) {
                        return roomId == 11L && (user.admin() || user.memberId() == 1002L);
                    }
                };
        interceptor = new StompAuthChannelInterceptor(tokenService, consultService);
    }

    private static Message<byte[]> frame(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Principal login(long memberId, String role) {
        return new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("CONNECT: 올바른 토큰이면 회원번호 · 권한을 연결에 붙인다")
    void connectWithValidTokenSetsUser() {
        String token = tokenService.issue(1002L, "test1234", "USER").token();

        Message<?> result = interceptor.preSend(connect("Bearer " + token), null);

        Principal user =
                MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class).getUser();
        assertThat(ConsultUser.from(user)).isEqualTo(new ConsultUser(1002L, false));
    }

    @Test
    @DisplayName("CONNECT: 토큰이 없거나 틀리면 막는다")
    void connectWithoutValidTokenIsDenied() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer wrong.token.value"), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SUBSCRIBE: 방 주인 · 관리자는 통과, 다른 회원은 막는다")
    void subscribeChecksRoomAccess() {
        interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/sub/consult/11", login(1002L, "USER")), null);
        interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/sub/consult/11", login(1L, "ADMIN")), null);

        assertThatThrownBy(
                        () ->
                                interceptor.preSend(
                                        frame(
                                                StompCommand.SUBSCRIBE,
                                                "/sub/consult/11",
                                                login(1003L, "USER")),
                                        null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SUBSCRIBE: 내 오류 큐는 통과 · 정해 두지 않은 주소 · 이상한 방 번호는 막는다")
    void subscribeOtherDestinationsAreDenied() {
        interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/user/queue/errors", login(1002L, "USER")), null);

        for (String destination :
                List.of("/sub/other", "/sub/consult/abc", "/sub/consult/0", "/sub/consult/")) {
            assertThatThrownBy(
                            () ->
                                    interceptor.preSend(
                                            frame(
                                                    StompCommand.SUBSCRIBE,
                                                    destination,
                                                    login(1002L, "USER")),
                                            null))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("SUBSCRIBE · SEND: 로그인 정보 없이 오면 막는다")
    void frameWithoutUserIsDenied() {
        assertThatThrownBy(
                        () ->
                                interceptor.preSend(
                                        frame(StompCommand.SUBSCRIBE, "/sub/consult/11", null),
                                        null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(
                        () ->
                                interceptor.preSend(
                                        frame(StompCommand.SEND, "/pub/consult/11", null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SEND: /pub/consult/{번호} 만 통과 · 브로커 주소(/sub · /user)로 직접 보내면 막는다")
    void sendOnlyToApplicationDestination() {
        interceptor.preSend(
                frame(StompCommand.SEND, "/pub/consult/11", login(1003L, "USER")), null);

        for (String destination :
                List.of(
                        "/sub/consult/11",
                        "/user/1002/queue/errors",
                        "/user/queue/errors",
                        "/queue/errors",
                        "/pub/other",
                        "/pub/consult/abc",
                        "/pub/consult/0")) {
            assertThatThrownBy(
                            () ->
                                    interceptor.preSend(
                                            frame(
                                                    StompCommand.SEND,
                                                    destination,
                                                    login(1003L, "USER")),
                                            null))
                    .as(destination)
                    .isInstanceOf(AccessDeniedException.class);
        }
        // 주소 없는 SEND 도 막음
        assertThatThrownBy(
                        () ->
                                interceptor.preSend(
                                        frame(StompCommand.SEND, null, login(1003L, "USER")), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("방 번호 꺼내기: /sub/consult/12 → 12 · 형식이 다르면 null")
    void roomIdOfParsesOnlyPositiveNumbers() {
        assertThat(StompAuthChannelInterceptor.roomIdOf("/sub/consult/12")).isEqualTo(12L);
        assertThat(StompAuthChannelInterceptor.roomIdOf("/sub/consult/-1")).isNull();
        assertThat(StompAuthChannelInterceptor.roomIdOf("/sub/consult/1/2")).isNull();
        assertThat(StompAuthChannelInterceptor.roomIdOf(null)).isNull();
        assertThat(StompAuthChannelInterceptor.roomIdOf("/pub/consult/12")).isNull();
        assertThat(StompAuthChannelInterceptor.numberAfter("/pub/consult/12", "/pub/consult/"))
                .isEqualTo(12L);
    }
}
