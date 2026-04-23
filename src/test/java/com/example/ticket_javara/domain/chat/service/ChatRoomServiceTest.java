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
import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import com.example.ticket_javara.global.exception.NotFoundException;
import com.example.ticket_javara.global.lock.DistributedLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatRoomService 단위 테스트")
class ChatRoomServiceTest {

    @InjectMocks
    private ChatRoomService chatRoomService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private DistributedLockProvider lockProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private User testUser;
    private ChatRoom testChatRoom;
    private ChatMessage testMessage;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .nickname("testUser")
                .role(UserRole.USER)
                .password("password")
                .build();
        // Reflection으로 ID 설정
        try {
            var field = User.class.getDeclaredField("userId");
            field.setAccessible(true);
            field.set(testUser, 1L);
        } catch (Exception e) {
            // ID 설정 실패 시 무시
        }

        testChatRoom = ChatRoom.builder()
                .user(testUser)
                .build();
        // Reflection으로 ID 설정
        try {
            var field = ChatRoom.class.getDeclaredField("chatRoomId");
            field.setAccessible(true);
            field.set(testChatRoom, 1L);
        } catch (Exception e) {
            // ID 설정 실패 시 무시
        }

        testMessage = ChatMessage.builder()
                .chatRoom(testChatRoom)
                .senderId(1L)
                .senderRole(ChatMessage.SenderRole.USER)
                .content("테스트 메시지")
                .build();
        // Reflection으로 ID 설정
        try {
            var field = ChatMessage.class.getDeclaredField("chatMessageId");
            field.setAccessible(true);
            field.set(testMessage, 1L);
        } catch (Exception e) {
            // ID 설정 실패 시 무시
        }
    }

    @Nested
    @DisplayName("채팅방 생성/조회 테스트")
    class CreateOrGetRoomTest {

        @Test
        @DisplayName("성공: 기존 활성 채팅방이 있으면 해당 방을 반환한다")
        void createOrGetRoom_ExistingRoom() {
            // given
            Long userId = 1L;
            given(chatRoomRepository.findLatestOpenRoomByUserId(userId))
                    .willReturn(Optional.of(testChatRoom));

            // when
            ChatRoomResponse response = chatRoomService.createOrGetRoom(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getChatRoomId()).isEqualTo(testChatRoom.getChatRoomId());
            assertThat(response.isNew()).isFalse();

            // 분산락이나 새 방 생성이 호출되지 않았는지 확인
            verify(lockProvider, never()).executeWithLock(anyString(), any());
            verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        }

        @Test
        @DisplayName("성공: 기존 방이 없으면 분산락을 사용하여 새 방을 생성한다")
        void createOrGetRoom_NewRoom() {
            // given
            Long userId = 1L;
            String lockKey = "lock:chat-room:create:" + userId;

            // 첫 번째 조회에서는 방이 없음
            given(chatRoomRepository.findLatestOpenRoomByUserId(userId))
                    .willReturn(Optional.empty());
            
            // userRepository는 분산락 밖에서 호출됨
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

            // 새 방 저장
            given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(testChatRoom);

            // 분산락 실행 시뮬레이션 - 실제 supplier를 실행
            given(lockProvider.executeWithLock(eq(lockKey), any(Supplier.class)))
                    .willAnswer(invocation -> {
                        Supplier<ChatRoomResponse> supplier = invocation.getArgument(1);
                        return supplier.get();
                    });

            // 트랜잭션 템플릿 실행 시뮬레이션 - 실제 function을 실행
            given(transactionTemplate.execute(any()))
                    .willAnswer(invocation -> {
                        org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
                        return callback.doInTransaction(null);
                    });

            // when
            ChatRoomResponse response = chatRoomService.createOrGetRoom(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getChatRoomId()).isEqualTo(testChatRoom.getChatRoomId());
            assertThat(response.isNew()).isTrue();

            verify(lockProvider).executeWithLock(eq(lockKey), any(Supplier.class));
            verify(transactionTemplate).execute(any());
            verify(userRepository).findById(userId);
            verify(chatRoomRepository).save(any(ChatRoom.class));
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없는 경우 NotFoundException 발생")
        void createOrGetRoom_UserNotFound() {
            // given
            Long userId = 999L;

            given(chatRoomRepository.findLatestOpenRoomByUserId(userId))
                    .willReturn(Optional.empty());
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatRoomService.createOrGetRoom(userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
            
            verify(userRepository).findById(userId);
        }
    }

    @Nested
    @DisplayName("채팅방 상태 업데이트 테스트")
    class UpdateRoomStatusTest {

        @Test
        @DisplayName("성공: 관리자가 채팅방 상태를 전이시킨다")
        void updateRoomStatus_Success() {
            // given
            Long chatRoomId = 1L;
            Long userId = 1L;
            String userRole = "ADMIN";
            ChatRoomStatus targetStatus = ChatRoomStatus.IN_PROGRESS;

            given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(testChatRoom));

            // AuthorizationUtil.requireCurrentUserAdmin() Mock
            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::requireCurrentUserAdmin)
                        .then(invocation -> null);

                // when
                ChatRoomResponse response = chatRoomService.updateRoomStatus(chatRoomId, targetStatus, userId, userRole);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getChatRoomId()).isEqualTo(chatRoomId);
                assertThat(response.isNew()).isFalse();

                verify(chatRoomRepository).findById(chatRoomId);
            }
        }

        @Test
        @DisplayName("실패: 채팅방을 찾을 수 없는 경우 NotFoundException 발생")
        void updateRoomStatus_ChatRoomNotFound() {
            // given
            Long chatRoomId = 999L;
            Long userId = 1L;
            String userRole = "ADMIN";
            ChatRoomStatus targetStatus = ChatRoomStatus.IN_PROGRESS;

            given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.empty());

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::requireCurrentUserAdmin)
                        .then(invocation -> null);

                // when & then
                assertThatThrownBy(() -> chatRoomService.updateRoomStatus(chatRoomId, targetStatus, userId, userRole))
                        .isInstanceOf(NotFoundException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ROOM_NOT_FOUND);
            }
        }
    }

    @Nested
    @DisplayName("채팅방 종료 테스트")
    class CloseChatRoomTest {

        @Test
        @DisplayName("성공: 채팅방 소유자가 WAITING 상태 방을 종료한다")
        void closeChatRoom_OwnerClosesWaitingRoom() {
            // given
            Long chatRoomId = 1L;
            Long userId = 1L;
            String userRole = "USER";

            given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(testChatRoom));

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::isCurrentUserAdmin)
                        .thenReturn(false);

                // when
                ChatRoomCloseResponse response = chatRoomService.closeChatRoom(chatRoomId, userId, userRole);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getMessage()).isEqualTo("채팅방이 종료되었습니다.");
                assertThat(response.getChatRoomId()).isEqualTo(chatRoomId);
                assertThat(response.getClosedAt()).isNotNull();

                verify(chatRoomRepository).findById(chatRoomId);
            }
        }

        @Test
        @DisplayName("성공: 관리자가 채팅방을 종료한다")
        void closeChatRoom_AdminCloses() {
            // given
            Long chatRoomId = 1L;
            Long userId = 2L; // 다른 사용자 ID
            String userRole = "ADMIN";

            given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(testChatRoom));

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::isCurrentUserAdmin)
                        .thenReturn(true);

                // when
                ChatRoomCloseResponse response = chatRoomService.closeChatRoom(chatRoomId, userId, userRole);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getMessage()).isEqualTo("채팅방이 종료되었습니다.");
                assertThat(response.getChatRoomId()).isEqualTo(chatRoomId);

                verify(chatRoomRepository).findById(chatRoomId);
            }
        }

        @Test
        @DisplayName("실패: 권한이 없는 사용자가 채팅방 종료를 시도하면 ForbiddenException 발생")
        void closeChatRoom_Unauthorized() {
            // given
            Long chatRoomId = 1L;
            Long userId = 2L; // 다른 사용자 ID
            String userRole = "USER";

            given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(testChatRoom));

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::isCurrentUserAdmin)
                        .thenReturn(false);

                // when & then
                assertThatThrownBy(() -> chatRoomService.closeChatRoom(chatRoomId, userId, userRole))
                        .isInstanceOf(ForbiddenException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_UNAUTHORIZED);
            }
        }

        @Test
        @DisplayName("실패: 이미 완료된 채팅방을 종료하려고 하면 BusinessException 발생")
        void closeChatRoom_AlreadyClosed() {
            // given
            Long chatRoomId = 1L;
            Long userId = 1L;
            String userRole = "USER";

            // 완료된 채팅방 설정
            try {
                var field = ChatRoom.class.getDeclaredField("status");
                field.setAccessible(true);
                field.set(testChatRoom, ChatRoomStatus.COMPLETED);
            } catch (Exception e) {
                // 설정 실패 시 무시
            }

            given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(testChatRoom));

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::isCurrentUserAdmin)
                        .thenReturn(false);

                // when & then
                assertThatThrownBy(() -> chatRoomService.closeChatRoom(chatRoomId, userId, userRole))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ROOM_ALREADY_CLOSED);
            }
        }
    }

    @Nested
    @DisplayName("채팅 내역 조회 테스트")
    class GetChatHistoryTest {

        @Test
        @DisplayName("성공: 커서 기반 페이징으로 채팅 내역을 조회한다")
        void getChatHistory_CursorPaging() {
            // given
            Long chatRoomId = 1L;
            Long cursor = 10L;
            Long afterId = null;
            int size = 20;
            Long userId = 1L;
            String userRole = "USER";

            List<ChatMessage> messages = List.of(testMessage);
            given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(testChatRoom));
            given(chatMessageRepository.getMessagesWithCursor(chatRoomId, cursor, size + 1))
                    .willReturn(messages);

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::isCurrentUserAdmin)
                        .thenReturn(false);
                authUtil.when(() -> com.example.ticket_javara.global.util.AuthorizationUtil.isAdmin("USER"))
                        .thenReturn(false);

                // when
                ChatHistoryResponse response = chatRoomService.getChatHistory(chatRoomId, cursor, afterId, size, userId, userRole);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getChatRoomId()).isEqualTo(chatRoomId);
                assertThat(response.getMessages()).hasSize(1);
                assertThat(response.isHasNext()).isFalse();
                assertThat(response.getNextCursor()).isEqualTo(testMessage.getChatMessageId());

                verify(chatMessageRepository).getMessagesWithCursor(chatRoomId, cursor, size + 1);
            }
        }

        @Test
        @DisplayName("성공: 재연결 복구 모드로 afterId 이후 메시지를 조회한다")
        void getChatHistory_ReconnectionMode() {
            // given
            Long chatRoomId = 1L;
            Long cursor = null;
            Long afterId = 5L;
            int size = 20;
            Long userId = 1L;
            String userRole = "USER";

            List<ChatMessage> messages = List.of(testMessage);
            given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(testChatRoom));
            given(chatMessageRepository.getMessagesAfter(chatRoomId, afterId))
                    .willReturn(messages);

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::isCurrentUserAdmin)
                        .thenReturn(false);
                authUtil.when(() -> com.example.ticket_javara.global.util.AuthorizationUtil.isAdmin("USER"))
                        .thenReturn(false);

                // when
                ChatHistoryResponse response = chatRoomService.getChatHistory(chatRoomId, cursor, afterId, size, userId, userRole);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getChatRoomId()).isEqualTo(chatRoomId);
                assertThat(response.getMessages()).hasSize(1);
                assertThat(response.isHasNext()).isFalse(); // 재연결 복구는 hasNext가 항상 false

                verify(chatMessageRepository).getMessagesAfter(chatRoomId, afterId);
            }
        }

        @Test
        @DisplayName("실패: cursor와 afterId를 동시에 제공하면 BusinessException 발생")
        void getChatHistory_InvalidRequest() {
            // given
            Long chatRoomId = 1L;
            Long cursor = 10L;
            Long afterId = 5L; // 둘 다 제공
            int size = 20;
            Long userId = 1L;
            String userRole = "USER";

            // when & then
            assertThatThrownBy(() -> chatRoomService.getChatHistory(chatRoomId, cursor, afterId, size, userId, userRole))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
        }

        @Test
        @DisplayName("실패: 권한이 없는 사용자가 채팅 내역을 조회하면 ForbiddenException 발생")
        void getChatHistory_Unauthorized() {
            // given
            Long chatRoomId = 1L;
            Long cursor = 10L;
            Long afterId = null;
            int size = 20;
            Long userId = 2L; // 다른 사용자 ID
            String userRole = "USER";

            given(chatRoomRepository.findById(chatRoomId)).willReturn(Optional.of(testChatRoom));

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::isCurrentUserAdmin)
                        .thenReturn(false);

                // when & then
                assertThatThrownBy(() -> chatRoomService.getChatHistory(chatRoomId, cursor, afterId, size, userId, userRole))
                        .isInstanceOf(ForbiddenException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_UNAUTHORIZED);
            }
        }
    }

    @Nested
    @DisplayName("관리자 채팅방 목록 조회 테스트")
    class GetAdminChatRoomsTest {

        @Test
        @DisplayName("성공: 관리자가 모든 채팅방 목록을 조회한다")
        void getAdminChatRooms_AllRooms() {
            // given
            String status = null; // 전체 조회
            Pageable pageable = PageRequest.of(0, 10);
            String userRole = "ADMIN";

            List<ChatRoom> rooms = List.of(testChatRoom);
            Page<ChatRoom> roomPage = new PageImpl<>(rooms, pageable, 1);
            List<ChatMessage> latestMessages = List.of(testMessage);

            given(chatRoomRepository.findAll(pageable)).willReturn(roomPage);
            given(chatMessageRepository.getLatestMessagesByRoomIds(anyList()))
                    .willReturn(latestMessages);

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::requireCurrentUserAdmin)
                        .then(invocation -> null);

                // when
                Page<AdminChatRoomResponse> response = chatRoomService.getAdminChatRooms(status, pageable, userRole);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getContent()).hasSize(1);
                assertThat(response.getTotalElements()).isEqualTo(1);

                verify(chatRoomRepository).findAll(pageable);
                verify(chatMessageRepository).getLatestMessagesByRoomIds(anyList());
            }
        }

        @Test
        @DisplayName("성공: 관리자가 특정 상태의 채팅방 목록을 조회한다")
        void getAdminChatRooms_ByStatus() {
            // given
            String status = "WAITING";
            Pageable pageable = PageRequest.of(0, 10);
            String userRole = "ADMIN";

            List<ChatRoom> rooms = List.of(testChatRoom);
            Page<ChatRoom> roomPage = new PageImpl<>(rooms, pageable, 1);
            List<ChatMessage> latestMessages = List.of(testMessage);

            given(chatRoomRepository.findByStatus(ChatRoomStatus.WAITING, pageable)).willReturn(roomPage);
            given(chatMessageRepository.getLatestMessagesByRoomIds(anyList()))
                    .willReturn(latestMessages);

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::requireCurrentUserAdmin)
                        .then(invocation -> null);

                // when
                Page<AdminChatRoomResponse> response = chatRoomService.getAdminChatRooms(status, pageable, userRole);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getContent()).hasSize(1);

                verify(chatRoomRepository).findByStatus(ChatRoomStatus.WAITING, pageable);
            }
        }

        @Test
        @DisplayName("실패: 잘못된 상태값으로 조회하면 BusinessException 발생")
        void getAdminChatRooms_InvalidStatus() {
            // given
            String status = "INVALID_STATUS";
            Pageable pageable = PageRequest.of(0, 10);
            String userRole = "ADMIN";

            try (MockedStatic<com.example.ticket_javara.global.util.AuthorizationUtil> authUtil = 
                 mockStatic(com.example.ticket_javara.global.util.AuthorizationUtil.class)) {
                
                authUtil.when(com.example.ticket_javara.global.util.AuthorizationUtil::requireCurrentUserAdmin)
                        .then(invocation -> null);

                // when & then
                assertThatThrownBy(() -> chatRoomService.getAdminChatRooms(status, pageable, userRole))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
            }
        }
    }
}