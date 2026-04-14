package com.example.ticket_javara.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import lombok.RequiredArgsConstructor;
import com.example.ticket_javara.global.security.StompHandler;

/**
 * WebSocket + STOMP 설정 (도전 기능 — CS 채팅)
 * - 엔드포인트: /ws-stomp (SockJS 폴백 지원)
 * - 구독 prefix: /sub
 * - 발행 prefix: /pub
 * 참고: 03_유스케이스_명세서.md UC-012
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandler stompHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // SockJS 폴백 지원
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 메시지 브로커 구독 prefix
        config.enableSimpleBroker("/sub");
        // 클라이언트 → 서버 메시지 발행 prefix
        config.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompHandler);
    }
}
