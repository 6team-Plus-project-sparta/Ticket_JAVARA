package com.example.ticket_javara.domain.chat.service;

import com.example.ticket_javara.domain.chat.dto.AdminChatRoomResponse;
import com.example.ticket_javara.domain.chat.dto.ChatHistoryResponse;
import com.example.ticket_javara.domain.chat.dto.ChatMessageResponse;
import com.example.ticket_javara.domain.chat.dto.ChatRoomResponse;
import com.example.ticket_javara.domain.chat.entity.ChatMessage;
import com.example.ticket_javara.domain.chat.entity.ChatRoom;
import com.example.ticket_javara.domain.chat.entity.ChatRoomStatus;
import com.example.ticket_javara.domain.chat.repository.ChatMessageRepository;
import com.example.ticket_javara.domain.chat.repository.ChatRoomRepository;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import com.example.ticket_javara.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ChatRoomResponse createOrGetRoom(Long userId) {
        Optional<ChatRoom> existingRoom = chatRoomRepository.findByUserUserIdAndStatus(userId, ChatRoomStatus.OPEN);
        
        if (existingRoom.isPresent()) {
            return ChatRoomResponse.of(existingRoom.get(), false);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));

        ChatRoom newRoom = ChatRoom.builder().user(user).build();
        ChatRoom savedRoom = chatRoomRepository.save(newRoom);

        return ChatRoomResponse.of(savedRoom, true);
    }

    @Transactional
    public void closeRoom(Long chatRoomId, Long userId, String userRole) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 권한 체크: ADMIN이거나, 본인 채팅방이어야 함
        if (!"ADMIN".equals(userRole) && !chatRoom.getUser().getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.CHAT_UNAUTHORIZED);
        }

        if (chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_CLOSED);
        }

        chatRoom.close();
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getChatHistory(Long chatRoomId, Long cursor, int size, Long userId, String userRole) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 권한 체크
        if (!"ADMIN".equals(userRole) && !chatRoom.getUser().getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.CHAT_UNAUTHORIZED);
        }

        // 실제 가져올 때 size + 1을 가져와서 hasNext 여부 판단
        List<ChatMessage> messages = chatMessageRepository.getMessagesWithCursor(chatRoomId, cursor, size + 1);

        boolean hasNext = messages.size() > size;
        if (hasNext) {
            messages.remove(size);
        }

        List<ChatMessageResponse> messageResponses = messages.stream()
                .map(msg -> {
                    String nickname = "ADMIN".equals(msg.getSenderRole().name())
                            ? "TicketFlow CS팀"
                            : chatRoom.getUser().getNickname();
                    return ChatMessageResponse.of(msg, nickname);
                })
                .collect(Collectors.toList());

        Long nextCursor = messages.isEmpty() ? null : messages.get(messages.size() - 1).getChatMessageId();

        return ChatHistoryResponse.builder()
                .chatRoomId(chatRoomId)
                .messages(messageResponses)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<AdminChatRoomResponse> getAdminChatRooms(String status, Pageable pageable, String userRole) {
        if (!"ADMIN".equals(userRole)) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }

        ChatRoomStatus statusEnum = null;
        try {
            if (status != null && !status.isEmpty()) {
                statusEnum = ChatRoomStatus.valueOf(status.toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Page<ChatRoom> rooms = statusEnum != null 
                ? chatRoomRepository.findByStatus(statusEnum, pageable)
                : chatRoomRepository.findAll(pageable);

        return rooms.map(room -> {
            // 마지막 메시지 조회 최적화 - 실제 규모가 커지면 역정규화 필드 (last_message) 추가 고려
            List<ChatMessage> msgs = chatMessageRepository.getMessagesWithCursor(room.getChatRoomId(), null, 1);
            String lastMsg = msgs.isEmpty() ? "" : msgs.get(0).getContent();
            return AdminChatRoomResponse.of(room, lastMsg);
        });
    }
}
