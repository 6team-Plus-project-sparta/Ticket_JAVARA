package com.example.ticket_javara.domain.chat.service;

import com.example.ticket_javara.domain.chat.dto.ChatMessageRequest;
import com.example.ticket_javara.domain.chat.dto.ChatMessageResponse;
import com.example.ticket_javara.domain.chat.entity.ChatMessage;
import com.example.ticket_javara.domain.chat.entity.ChatMessage.SenderRole;
import com.example.ticket_javara.domain.chat.entity.ChatRoom;
import com.example.ticket_javara.domain.chat.entity.ChatRoomStatus;
import com.example.ticket_javara.domain.chat.repository.ChatMessageRepository;
import com.example.ticket_javara.domain.chat.repository.ChatRoomRepository;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void saveAndSendMessage(Long senderId, String senderRoleParam, ChatMessageRequest request) {
        Long chatRoomId = request.getChatRoomId();

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_CLOSED);
        }

        SenderRole role = "ADMIN".equals(senderRoleParam) ? SenderRole.ADMIN : SenderRole.USER;

        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .senderId(senderId)
                .senderRole(role)
                .content(request.getContent())
                .build();

        chatMessageRepository.save(message);

        String senderNickname = role == SenderRole.ADMIN ? "TicketFlow CS팀" : chatRoom.getUser().getNickname();
        ChatMessageResponse response = ChatMessageResponse.of(message, senderNickname);

        // 해당 채팅방을 구독 중인 연결로 브로드캐스트
        messagingTemplate.convertAndSend("/sub/chat/room/" + chatRoomId, response);
    }
}
