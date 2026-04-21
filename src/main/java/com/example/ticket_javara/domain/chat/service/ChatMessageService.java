package com.example.ticket_javara.domain.chat.service;

import com.example.ticket_javara.domain.chat.dto.ChatMessageRequest;
import com.example.ticket_javara.domain.chat.dto.ChatMessageResponse;
import com.example.ticket_javara.domain.chat.entity.ChatMessage;
import com.example.ticket_javara.domain.chat.entity.ChatMessage.SenderRole;
import com.example.ticket_javara.domain.chat.entity.ChatRoom;
import com.example.ticket_javara.domain.chat.repository.ChatMessageRepository;
import com.example.ticket_javara.domain.chat.repository.ChatRoomRepository;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.NotFoundException;
import com.example.ticket_javara.global.util.AuthorizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * SA 문서 FN-CHAT-02: "Spring 내장 Simple Broker 사용 (외부 메시지 브로커 불필요)"
     * 단일 서버 환경에서는 SimpMessagingTemplate으로 직접 브로드캐스트.
     * 다중 서버 확장 시 Redis Pub/Sub(RedisChatPublisher)로 교체 가능.
     */
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void saveAndSendMessage(Long senderId, String senderRoleParam, ChatMessageRequest request) {
        Long chatRoomId = request.getChatRoomId();

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // COMPLETED(완료) 상태의 채팅방에는 메시지 전송 불가
        if (!chatRoom.isActive()) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_CLOSED);
        }

        // STOMP 세션은 SecurityContext가 비어있을 수 있으므로 컨트롤러에서 전달받은 senderRoleParam을 검증
        SenderRole role = ("ROLE_ADMIN".equals(senderRoleParam) || "ADMIN".equals(senderRoleParam)) 
                ? SenderRole.ADMIN : SenderRole.USER;

        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .senderId(senderId)
                .senderRole(role)
                .content(request.getContent())
                .build();

        chatMessageRepository.save(message);

        String senderNickname = role == SenderRole.ADMIN ? "TicketJavara CS팀" : chatRoom.getUser().getNickname();
        ChatMessageResponse response = ChatMessageResponse.of(message, senderNickname);

        // Simple Broker로 직접 브로드캐스트
        messagingTemplate.convertAndSend("/sub/chat/room/" + chatRoomId, response);
        log.debug("[ChatMessageService] 메시지 발송 완료 - chatRoomId: {}, senderId: {}", chatRoomId, senderId);
    }
}
