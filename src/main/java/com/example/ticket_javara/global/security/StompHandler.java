package com.example.ticket_javara.global.security;

import com.example.ticket_javara.domain.chat.service.SystemMessageService;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class StompHandler implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private SystemMessageService systemMessageService;

    public StompHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // 순환 참조 방지를 위해 @Lazy 주입
    @Autowired
    public void setSystemMessageService(@Lazy SystemMessageService systemMessageService) {
        this.systemMessageService = systemMessageService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
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

            Long userId = jwtUtil.getUserId(token);
            String role = jwtUtil.getRole(token);
            String email = jwtUtil.getEmail(token);

            CustomUserDetails customUserDetails = new CustomUserDetails(userId, email, role);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());

            accessor.setUser(authentication);
            log.debug("[StompHandler] CONNECT - userId: {}", userId);

        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            // 퇴장 시스템 메시지 전송 (세션 속성에서 정보 읽기)
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs != null && systemMessageService != null) {
                Object chatRoomIdObj = sessionAttrs.get("chatRoomId");
                Object nicknameObj = sessionAttrs.get("nickname");

                if (chatRoomIdObj instanceof Long chatRoomId && nicknameObj instanceof String nickname) {
                    try {
                        systemMessageService.sendLeaveMessage(chatRoomId, nickname);
                    } catch (Exception e) {
                        log.warn("[StompHandler] 퇴장 메시지 전송 실패 - chatRoomId: {}, error: {}", chatRoomId, e.getMessage());
                    }
                }
            }
            log.debug("[StompHandler] DISCONNECT 처리 완료");
        }

        return message;
    }
}
