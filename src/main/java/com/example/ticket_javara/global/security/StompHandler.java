package com.example.ticket_javara.global.security;

import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
            
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                log.warn("[StompHandler] 토큰 누락 또는 형식 오류");
                throw new ForbiddenException(ErrorCode.INVALID_TOKEN);
            }

            String token = authorizationHeader.substring(7);
            
            if (jwtUtil.validateToken(token) != JwtUtil.TokenStatus.VALID) {
                log.warn("[StompHandler] 유효하지 않은 STOMP 토큰 연결 시도");
                throw new ForbiddenException(ErrorCode.INVALID_TOKEN);
            }
            
            // 토큰이 유효하면 Principal을 생성하여 세션에 명시적 주입
            Long userId = jwtUtil.getUserId(token);
            String role = jwtUtil.getRole(token);
            String email = jwtUtil.getEmail(token);

            CustomUserDetails customUserDetails = new CustomUserDetails(userId, email, role);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());

            accessor.setUser(authentication);
        }

        return message;
    }
}
