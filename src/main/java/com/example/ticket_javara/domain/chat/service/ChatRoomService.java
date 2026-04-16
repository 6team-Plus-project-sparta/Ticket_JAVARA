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
import com.example.ticket_javara.global.lock.DistributedLockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DistributedLockProvider lockProvider; // 분산락 의존성 추가
    private final UserRepository userRepository;

    /**
     * 사용자의 기존 OPEN 채팅방을 반환하거나, 없으면 새로 생성합니다. (분산락 적용)
     */
    @Transactional
    public ChatRoomResponse createOrGetRoom(Long userId) {
        // 1. 1차 조회: 기존 방이 있으면 isNew = false 로 반환
        return chatRoomRepository.findFirstByUserUserIdAndStatusOrderByCreatedAtDesc(userId, ChatRoomStatus.OPEN)
                .map(room -> ChatRoomResponse.of(room, false))
                .orElseGet(() -> {
                    String lockKey = "lock:chat-room:create:" + userId;

                    User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));

                    return lockProvider.executeWithLock(lockKey, () -> {
                        // 2. 2차 조회: 락 대기 중 다른 스레드가 방을 만들었으면 isNew = false 로 반환
                        return chatRoomRepository.findFirstByUserUserIdAndStatusOrderByCreatedAtDesc(userId, ChatRoomStatus.OPEN)
                                .map(room -> ChatRoomResponse.of(room, false))
                                .orElseGet(() -> {
                                    // 3. 최종 생성: 진짜 방이 없어서 새로 만들었으므로 isNew = true 로 반환
                                    ChatRoom newRoom = ChatRoom.builder()
                                            .user(user)
                                            .build();
                                    return ChatRoomResponse.of(chatRoomRepository.save(newRoom), true);
                                });
                    });
                });
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
        
        // 메모리 보호 (OOM 방지)
        int limitedSize = Math.min(size, 100);

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // 권한 체크
        if (!"ADMIN".equals(userRole) && !chatRoom.getUser().getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.CHAT_UNAUTHORIZED);
        }

        // 실제 가져올 때 size + 1을 가져와서 hasNext 여부 판단
        List<ChatMessage> messages = chatMessageRepository.getMessagesWithCursor(chatRoomId, cursor, limitedSize + 1);

        boolean hasNext = messages.size() > limitedSize;
        if (hasNext) {
            messages.remove(limitedSize);
        }

        List<ChatMessageResponse> messageResponses = messages.stream()
                .map(msg -> {
                    String nickname = "ADMIN".equals(msg.getSenderRole().name())
                            ? "TicketJavara CS팀"
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
