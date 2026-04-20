package com.example.ticket_javara.global.security;

import com.example.ticket_javara.domain.chat.service.SystemMessageService;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * STOMP ChannelInterceptor — 인증/인가 처리
 *
 * preSend()에서 CONNECT 시 JWT 검증 + principal 설정.
 * 미인증 요청은 Controller 도달 전에 차단된다.
 *
 * CONNECT 시 nickname을 DB에서 한 번만 조회해 세션에 저장.
 * → ChatMessageController.joinRoom()에서 DB 재조회 불필요 (항목 4 개선)
 */
@Slf4j
@Component
public class StompHandler implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private SystemMessageService systemMessageService;

    public StompHandler(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    /** 순환 참조 방지: SystemMessageService → SimpMessagingTemplate → WebSocket 관련 빈 */
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
                throw new MessageDeliveryException("STOMP CONNECT unauthorized: missing/invalid Authorization header");
            }

            String token = authorizationHeader.substring(7);

            if (jwtUtil.validateToken(token) != JwtUtil.TokenStatus.VALID) {
                log.warn("[StompHandler] 유효하지 않은 STOMP 토큰 연결 시도");
                throw new MessageDeliveryException("STOMP CONNECT unauthorized: invalid token");
            }

            Long userId = jwtUtil.getUserId(token);
            String role  = jwtUtil.getRole(token);
            String email = jwtUtil.getEmail(token);

            CustomUserDetails customUserDetails = new CustomUserDetails(userId, email, role);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());

            accessor.setUser(authentication);

            // ★ 항목 4 개선: 연결당 한 번만 nickname 조회 → 세션 속성에 저장
            // ChatMessageController.joinRoom()에서 이 값을 읽어 DB 재조회를 생략한다.
            String nickname = userRepository.findById(userId)
                    .map(com.example.ticket_javara.domain.user.entity.User::getNickname)
                    .orElseThrow(() -> new MessageDeliveryException("STOMP CONNECT unauthorized: user not found"));
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs != null) {
                sessionAttrs.put("stomp_nickname", nickname);
            }

            log.debug("[StompHandler] CONNECT - userId: {}, nickname: {}", userId, nickname);

        } else if (StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                || StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())) {
            // CONNECT 단계에서 principal이 세팅되지 않은 세션은 이후 단계에서 더 이상 진행시키지 않는다.
            if (accessor.getUser() == null) {
                throw new MessageDeliveryException("STOMP message unauthorized: principal is null");
            }
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            // 퇴장 시스템 메시지 전송 (세션 속성에서 정보 읽기)
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs != null && systemMessageService != null) {
                Object chatRoomIdObj = sessionAttrs.get("chatRoomId");
                Object nicknameObj   = sessionAttrs.get("nickname");

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
