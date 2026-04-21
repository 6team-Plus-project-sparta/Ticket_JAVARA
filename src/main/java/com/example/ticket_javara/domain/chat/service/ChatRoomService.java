package com.example.ticket_javara.domain.chat.service;

import com.example.ticket_javara.domain.chat.dto.AdminChatRoomResponse;
import com.example.ticket_javara.domain.chat.dto.ChatHistoryResponse;
import com.example.ticket_javara.domain.chat.dto.ChatMessageResponse;
import com.example.ticket_javara.domain.chat.dto.ChatRoomCloseResponse;
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
import com.example.ticket_javara.global.util.AuthorizationUtil;
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
    private final DistributedLockProvider lockProvider;
    private final UserRepository userRepository;

    /**
     * 사용자의 기존 WAITING 채팅방 반환 또는 새로 생성 (분산락 적용)
     */
    public ChatRoomResponse createOrGetRoom(Long userId) {
        return chatRoomRepository.findLatestOpenRoomByUserId(userId)
                .map(room -> ChatRoomResponse.of(room, false))
                .orElseGet(() -> {
                    String lockKey = "lock:chat-room:create:" + userId;
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));

                    return lockProvider.executeWithLock(lockKey, () ->
                            chatRoomRepository.findLatestOpenRoomByUserId(userId)
                                    .map(room -> ChatRoomResponse.of(room, false))
                                    .orElseGet(() -> {
                                        ChatRoom newRoom = ChatRoom.builder().user(user).build();
                                        return ChatRoomResponse.of(chatRoomRepository.save(newRoom), true);
                                    })
                    );
                });
    }

    /**
     * 관리자가 채팅방 상태를 전이 (WAITING → IN_PROGRESS → COMPLETED)
     * 역방향 전이 시 BusinessException(INVALID_CHAT_STATUS_TRANSITION) 발생
     */
    /**
     * 관리자가 채팅방 상태를 전이 (WAITING → IN_PROGRESS → COMPLETED)
     * 역방향 전이 시 BusinessException(INVALID_CHAT_STATUS_TRANSITION) 발생.
     */
    @Transactional
    public ChatRoomResponse updateRoomStatus(Long chatRoomId, ChatRoomStatus targetStatus, Long userId, String userRole) {
        // Spring Security 기반 권한 검증 (Controller의 @PreAuthorize와 이중 검증)
        AuthorizationUtil.requireCurrentUserAdmin();

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        chatRoom.updateStatus(targetStatus); // 엔티티 내부에서 전이 유효성 검증 + closedAt 자동 설정
        return ChatRoomResponse.of(chatRoom, false);
    }

    /**
     * 고객(또는 ADMIN): 채팅방 종료
     * - WAITING 상태면 IN_PROGRESS를 거쳐 COMPLETED로 전이 (상태머신 규칙 유지)
     * - IN_PROGRESS 상태면 COMPLETED로 전이
     * - COMPLETED면 에러
     */
    @Transactional
    public ChatRoomCloseResponse closeChatRoom(Long chatRoomId, Long userId, String userRole) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        // Spring Security 기반 권한 검증 + 소유자 확인
        boolean isAdmin = AuthorizationUtil.isCurrentUserAdmin();
        boolean isOwner = chatRoom.getUser().getUserId().equals(userId);
        if (!isAdmin && !isOwner) {
            throw new ForbiddenException(ErrorCode.CHAT_UNAUTHORIZED);
        }

        if (chatRoom.getStatus() == ChatRoomStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ALREADY_CLOSED);
        }

        if (chatRoom.getStatus() == ChatRoomStatus.WAITING) {
            chatRoom.updateStatus(ChatRoomStatus.IN_PROGRESS);
        }
        chatRoom.updateStatus(ChatRoomStatus.COMPLETED); // closedAt 자동 설정됨

        return ChatRoomCloseResponse.builder()
                .message("채팅방이 종료되었습니다.")
                .chatRoomId(chatRoomId)
                .chatRoom(ChatRoomResponse.of(chatRoom, false))
                .closedAt(chatRoom.getClosedAt()) // 엔티티에서 실제 저장된 시간 사용
                .build();
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getChatHistory(Long chatRoomId, Long cursor, Long afterId, int size, Long userId, String userRole) {
        if (cursor != null && afterId != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        int limitedSize = Math.min(size, 100);

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!AuthorizationUtil.isCurrentUserAdmin() && !chatRoom.getUser().getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.CHAT_UNAUTHORIZED);
        }

        List<ChatMessage> messages;
        boolean hasNext;

        if (afterId != null) {
            // 재연결 복구 모드: afterId 이후 메시지를 오름차순으로 반환
            messages = chatMessageRepository.getMessagesAfter(chatRoomId, afterId);
            hasNext = false; // 재연결 복구는 전체 누락분을 한번에 반환
        } else {
            // 일반 이력 조회 모드: cursor 기반 페이징
            messages = chatMessageRepository.getMessagesWithCursor(chatRoomId, cursor, limitedSize + 1);
            hasNext = messages.size() > limitedSize;
            if (hasNext) {
                messages.remove(limitedSize);
            }
        }

        List<ChatMessageResponse> messageResponses = messages.stream()
                .map(msg -> {
                    String nickname = AuthorizationUtil.isAdmin(msg.getSenderRole().name())
                            ? "TicketJavara CS팀"
                            : "SYSTEM".equals(msg.getSenderRole().name())
                            ? "시스템"
                            : chatRoom.getUser().getNickname();
                    return ChatMessageResponse.of(msg, nickname);
                })
                .collect(Collectors.toList());

        Long nextCursor = (!messages.isEmpty() && cursor != null)
                ? messages.get(messages.size() - 1).getChatMessageId()
                : null;

        return ChatHistoryResponse.builder()
                .chatRoomId(chatRoomId)
                .messages(messageResponses)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<AdminChatRoomResponse> getAdminChatRooms(String status, Pageable pageable, String userRole) {
        // Spring Security 기반 권한 검증 (Controller의 @PreAuthorize와 이중 검증)
        AuthorizationUtil.requireCurrentUserAdmin();

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

        List<Long> roomIds = rooms.getContent().stream()
                .map(ChatRoom::getChatRoomId)
                .collect(Collectors.toList());

        List<ChatMessage> latestMessages = chatMessageRepository.getLatestMessagesByRoomIds(roomIds);

        var messageMap = latestMessages.stream()
                .collect(Collectors.toMap(
                        msg -> msg.getChatRoom().getChatRoomId(),
                        ChatMessage::getContent
                ));

        return rooms.map(room -> {
            String lastMsg = messageMap.getOrDefault(room.getChatRoomId(), "");
            return AdminChatRoomResponse.of(room, lastMsg);
        });
    }
}
