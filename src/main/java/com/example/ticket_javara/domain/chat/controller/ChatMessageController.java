package com.example.ticket_javara.domain.chat.controller;

import com.example.ticket_javara.domain.chat.dto.ChatMessageRequest;
import com.example.ticket_javara.domain.chat.service.ChatMessageService;
import com.example.ticket_javara.domain.chat.service.SystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import jakarta.validation.Valid;

import java.security.Principal;
import java.util.Map;
import com.example.ticket_javara.domain.chat.dto.ChatRoomJoinRequest;
import com.example.ticket_javara.global.security.CustomUserDetails;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final SystemMessageService systemMessageService;
    // ★ 항목 4 개선: UserRepository 제거 — nickname은 StompHandler.CONNECT 시 세션에 저장되므로 DB 재조회 불필요

    @MessageMapping("/chat/message")
    public void sendMessage(@Payload @Valid ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            log.warn("[STOMP] 인증되지 않은 세션의 메시지 전송 시도");
            throw new MessageDeliveryException("STOMP SEND unauthorized: principal is null");
        }

        CustomUserDetails userDetails = (CustomUserDetails) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        chatMessageService.saveAndSendMessage(userDetails.getUserId(), userDetails.getRole(), request);
    }

    /**
     * 채팅방 입장 알림
     * 클라이언트가 /sub/chat/room/{chatRoomId} 구독 직후 이 엔드포인트를 호출.
     * STOMP 세션 속성에 chatRoomId, nickname을 저장하여
     * DISCONNECT 시 StompHandler에서 퇴장 메시지를 자동 발행.
     * Payload: { "chatRoomId": 1 }
     *
     * nickname은 StompHandler.CONNECT 시 DB에서 한 번만 조회하여 세션("stomp_nickname")에 저장됨.
     * 반복 DB 조회 없이 세션에서 꺼내 사용한다.
     */
    @MessageMapping("/chat/join")
    public void joinRoom(@Payload @Valid ChatRoomJoinRequest payload,
                         Principal principal,
                         SimpMessageHeaderAccessor headerAccessor) {
        if (principal == null) {
            log.warn("[STOMP] 인증되지 않은 입장 시도");
            throw new MessageDeliveryException("STOMP JOIN unauthorized: principal is null");
        }

        Long chatRoomId = payload.getChatRoomId();

        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs == null) {
            log.error("[STOMP] WebSocket 세션이 초기화되지 않은 비정상적 요청");
            throw new MessageDeliveryException("STOMP JOIN invalid session: session attributes missing");
        }

        CustomUserDetails userDetails = (CustomUserDetails) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();

        // ★ StompHandler.CONNECT 시 세션에 저장된 nickname 사용 (DB 재조회 없음)
        Object nicknameObj = sessionAttrs.get("stomp_nickname");
        if (!(nicknameObj instanceof String nickname) || nickname.isBlank()) {
            throw new MessageDeliveryException("STOMP JOIN invalid session: nickname missing");
        }

        // 퇴장 메시지용 세션 속성 저장 → StompHandler.DISCONNECT 에서 읽음
        sessionAttrs.put("chatRoomId", chatRoomId);
        sessionAttrs.put("nickname", nickname);

        systemMessageService.sendJoinMessage(chatRoomId, nickname);
        log.info("[STOMP] JOIN - chatRoomId: {}, userId: {}, nickname: {}", chatRoomId, userDetails.getUserId(), nickname);
    }
}
