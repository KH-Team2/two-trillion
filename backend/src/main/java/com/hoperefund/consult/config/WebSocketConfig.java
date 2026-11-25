package com.hoperefund.consult.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * [추가 11/24 · 이우진 · TT-153] 상담 채팅 WebSocket · STOMP 설정
 *
 * <p>연결: /ws · 보내기(화면 → 서버 컨트롤러): /pub/... · 받기(구독): /sub/... · 나에게만: /user/queue/...
 *
 * <p>주소 이름은 강사님 2차 미션 채팅 교안(/pub · /sub)을 따름. /ws 는 /api/ 가 아니라 SecurityConfig 에서 열려 있고, 로그인 확인은
 * STOMP CONNECT 때 StompAuthChannelInterceptor 가 함 (브라우저 WebSocket 은 Authorization 헤더를 못 실어서).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final String[] allowedOrigins; // [추가 11/25 · TT-179] CorsConfig 와 같은 설정값

    public WebSocketConfig(
            StompAuthChannelInterceptor stompAuthChannelInterceptor,
            @Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 허용한 화면 주소만 (CorsConfig 와 같은 설정값 · 기본 localhost:5173) · 다른 사이트가 우리 서버에 WebSocket 으로 붙지 못하게
        // [수정 11/25 · TT-179] 코드에 고정했던 주소 → application.yml cors.allowed-origins
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /sub/consult/{roomId}: 방 구독자에게 전달 · /queue: 보낸 사람에게만 (/user/queue/errors)
        // 서버 메모리 안 기본 브로커 → 서버 1대 기준 (SSE 알림과 같은 한계)
        registry.enableSimpleBroker("/sub", "/queue");
        // /pub/... 로 온 메시지는 @MessageMapping 컨트롤러(ConsultMessageController)로
        registry.setApplicationDestinationPrefixes("/pub");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 화면에서 들어오는 모든 STOMP 프레임(CONNECT · SUBSCRIBE · SEND)을 먼저 검사
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
