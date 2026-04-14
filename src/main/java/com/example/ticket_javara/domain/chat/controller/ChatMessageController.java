package com.example.ticket_javara.domain.chat.controller;

import com.example.ticket_javara.domain.chat.dto.ChatMessageRequest;
import com.example.ticket_javara.domain.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import jakarta.validation.Valid;

import java.security.Principal;
import com.example.ticket_javara.global.security.CustomUserDetails;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/chat/message")
    public void sendMessage(@Payload @Valid ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            log.warn("[STOMP] 인증되지 않은 세션의 메시지 전송 시도");
            return;
        }

        CustomUserDetails userDetails = (CustomUserDetails) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        Long userId = userDetails.getUserId();
        String role = userDetails.getRole();

        chatMessageService.saveAndSendMessage(userId, role, request);
    }
}
