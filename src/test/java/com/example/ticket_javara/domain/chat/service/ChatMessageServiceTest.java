package com.example.ticket_javara.domain.chat.service;

import com.example.ticket_javara.domain.chat.dto.ChatMessageRequest;
import com.example.ticket_javara.domain.chat.dto.ChatMessageResponse;
import com.example.ticket_javara.domain.chat.entity.ChatMessage;
import com.example.ticket_javara.domain.chat.entity.ChatRoom;
import com.example.ticket_javara.domain.chat.entity.ChatRoomStatus;
import com.example.ticket_javara.domain.chat.repository.ChatMessageRepository;
import com.example.ticket_javara.domain.chat.repository.ChatRoomRepository;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.NotFoundException;
import com.example.ticket_javara.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMessageService 단위 테스트")
class ChatMessageServiceTest {

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private CustomUserDetails userDetails;

    private User testUser;
    private ChatRoom testChatRoom;
    private ChatMessageRequest messageRequest;

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
        // Reflection으로 ID와 상태 설정
        try {
            var idField = ChatRoom.class.getDeclaredField("chatRoomId");
            idField.setAccessible(true);
            idField.set(testChatRoom, 1L);

            var statusField = ChatRoom.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(testChatRoom, ChatRoomStatus.WAITING);
        } catch (Exception e) {
            // 설정 실패 시 무시
        }

        // ChatMessageRequest 생성 (Reflection 사용)
        messageRequest = createChatMessageRequest(1L, "테스트 메시지");
    }

    @Nested
    @DisplayName("메시지 전송 테스트")
    class SaveAndSendMessageTest {

        @Test
        @DisplayName("성공: 일반 사용자가 활성 채팅방에 메시지를 전송한다")
        void saveAndSendMessage_UserSuccess() {
            // given
            Long userId = 1L;
            Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

            given(userDetails.getUserId()).willReturn(userId);
            given(userDetails.getAuthorities()).willReturn((Collection) authorities);
            given(chatRoomRepository.findById(1L)).willReturn(Optional.of(testChatRoom));

            ChatMessage savedMessage = ChatMessage.builder()
                    .chatRoom(testChatRoom)
                    .senderId(userId)
                    .senderRole(ChatMessage.SenderRole.USER)
                    .content("테스트 메시지")
                    .build();
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(savedMessage);

            // when
            chatMessageService.saveAndSendMessage(userDetails, messageRequest);

            // then
            // 메시지 저장 검증
            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository).save(messageCaptor.capture());
            
            ChatMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getChatRoom()).isEqualTo(testChatRoom);
            assertThat(capturedMessage.getSenderId()).isEqualTo(userId);
            assertThat(capturedMessage.getSenderRole()).isEqualTo(ChatMessage.SenderRole.USER);
            assertThat(capturedMessage.getContent()).isEqualTo("테스트 메시지");

            // WebSocket 브로드캐스트 검증
            ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<ChatMessageResponse> responseCaptor = ArgumentCaptor.forClass(ChatMessageResponse.class);
            verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), responseCaptor.capture());
            
            assertThat(destinationCaptor.getValue()).isEqualTo("/sub/chat/room/1");
            
            ChatMessageResponse capturedResponse = responseCaptor.getValue();
            assertThat(capturedResponse.getContent()).isEqualTo("테스트 메시지");
            assertThat(capturedResponse.getSenderNickname()).isEqualTo("testUser");
        }

        @Test
        @DisplayName("성공: 관리자가 활성 채팅방에 메시지를 전송한다")
        void saveAndSendMessage_AdminSuccess() {
            // given
            Long userId = 2L;
            Collection<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );

            given(userDetails.getUserId()).willReturn(userId);
            given(userDetails.getAuthorities()).willReturn((Collection) authorities);
            given(chatRoomRepository.findById(1L)).willReturn(Optional.of(testChatRoom));

            ChatMessage savedMessage = ChatMessage.builder()
                    .chatRoom(testChatRoom)
                    .senderId(userId)
                    .senderRole(ChatMessage.SenderRole.ADMIN)
                    .content("테스트 메시지")
                    .build();
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(savedMessage);

            // when
            chatMessageService.saveAndSendMessage(userDetails, messageRequest);

            // then
            // 메시지 저장 검증 (관리자 역할)
            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository).save(messageCaptor.capture());
            
            ChatMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getSenderRole()).isEqualTo(ChatMessage.SenderRole.ADMIN);

            // WebSocket 브로드캐스트 검증 (관리자 닉네임)
            ArgumentCaptor<ChatMessageResponse> responseCaptor = ArgumentCaptor.forClass(ChatMessageResponse.class);
            verify(messagingTemplate).convertAndSend(eq("/sub/chat/room/1"), responseCaptor.capture());
            
            ChatMessageResponse capturedResponse = responseCaptor.getValue();
            assertThat(capturedResponse.getSenderNickname()).isEqualTo("TicketJavara CS팀");
        }

        @Test
        @DisplayName("성공: IN_PROGRESS 상태의 채팅방에 메시지를 전송한다")
        void saveAndSendMessage_InProgressRoom() {
            // given
            Long userId = 1L;
            Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

            // 채팅방 상태를 IN_PROGRESS로 변경
            try {
                var statusField = ChatRoom.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(testChatRoom, ChatRoomStatus.IN_PROGRESS);
            } catch (Exception e) {
                // 설정 실패 시 무시
            }

            given(userDetails.getUserId()).willReturn(userId);
            given(userDetails.getAuthorities()).willReturn((Collection) authorities);
            given(chatRoomRepository.findById(1L)).willReturn(Optional.of(testChatRoom));

            ChatMessage savedMessage = ChatMessage.builder()
                    .chatRoom(testChatRoom)
                    .senderId(userId)
                    .senderRole(ChatMessage.SenderRole.USER)
                    .content("테스트 메시지")
                    .build();
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(savedMessage);

            // when
            chatMessageService.saveAndSendMessage(userDetails, messageRequest);

            // then
            verify(chatMessageRepository).save(any(ChatMessage.class));
            verify(messagingTemplate).convertAndSend(eq("/sub/chat/room/1"), any(ChatMessageResponse.class));
        }

        @Test
        @DisplayName("실패: 채팅방을 찾을 수 없는 경우 NotFoundException 발생")
        void saveAndSendMessage_ChatRoomNotFound() {
            // given
            Long userId = 1L;
            Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

            given(chatRoomRepository.findById(1L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatMessageService.saveAndSendMessage(userDetails, messageRequest))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ROOM_NOT_FOUND);

            verify(chatMessageRepository, never()).save(any(ChatMessage.class));
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(ChatMessageResponse.class));
        }

        @Test
        @DisplayName("실패: 완료된 채팅방에 메시지 전송 시 BusinessException 발생")
        void saveAndSendMessage_CompletedRoom() {
            // given
            Long userId = 1L;
            Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

            // 채팅방 상태를 COMPLETED로 변경
            try {
                var statusField = ChatRoom.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(testChatRoom, ChatRoomStatus.COMPLETED);
            } catch (Exception e) {
                // 설정 실패 시 무시
            }

            given(chatRoomRepository.findById(1L)).willReturn(Optional.of(testChatRoom));

            // when & then
            assertThatThrownBy(() -> chatMessageService.saveAndSendMessage(userDetails, messageRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ROOM_ALREADY_CLOSED);

            verify(chatMessageRepository, never()).save(any(ChatMessage.class));
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(ChatMessageResponse.class));
        }

        @Test
        @DisplayName("성공: 빈 권한 목록을 가진 사용자는 USER 역할로 처리된다")
        void saveAndSendMessage_EmptyAuthorities() {
            // given
            Long userId = 1L;
            Collection<GrantedAuthority> authorities = List.of(); // 빈 권한 목록

            given(userDetails.getUserId()).willReturn(userId);
            given(userDetails.getAuthorities()).willReturn((Collection) authorities);
            given(chatRoomRepository.findById(1L)).willReturn(Optional.of(testChatRoom));

            ChatMessage savedMessage = ChatMessage.builder()
                    .chatRoom(testChatRoom)
                    .senderId(userId)
                    .senderRole(ChatMessage.SenderRole.USER)
                    .content("테스트 메시지")
                    .build();
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(savedMessage);

            // when
            chatMessageService.saveAndSendMessage(userDetails, messageRequest);

            // then
            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository).save(messageCaptor.capture());
            
            ChatMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getSenderRole()).isEqualTo(ChatMessage.SenderRole.USER);
        }

        @Test
        @DisplayName("성공: 다른 권한을 가진 사용자도 USER 역할로 처리된다")
        void saveAndSendMessage_OtherAuthorities() {
            // given
            Long userId = 1L;
            Collection<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_MODERATOR") // ADMIN이 아닌 다른 권한
            );

            given(userDetails.getUserId()).willReturn(userId);
            given(userDetails.getAuthorities()).willReturn((Collection) authorities);
            given(chatRoomRepository.findById(1L)).willReturn(Optional.of(testChatRoom));

            ChatMessage savedMessage = ChatMessage.builder()
                    .chatRoom(testChatRoom)
                    .senderId(userId)
                    .senderRole(ChatMessage.SenderRole.USER)
                    .content("테스트 메시지")
                    .build();
            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(savedMessage);

            // when
            chatMessageService.saveAndSendMessage(userDetails, messageRequest);

            // then
            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository).save(messageCaptor.capture());
            
            ChatMessage capturedMessage = messageCaptor.getValue();
            assertThat(capturedMessage.getSenderRole()).isEqualTo(ChatMessage.SenderRole.USER);
        }
    }

    private ChatMessageRequest createChatMessageRequest(Long chatRoomId, String content) {
        try {
            // protected 생성자에 접근하기 위해 setAccessible 사용
            var constructor = ChatMessageRequest.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ChatMessageRequest request = constructor.newInstance();
            
            var chatRoomIdField = ChatMessageRequest.class.getDeclaredField("chatRoomId");
            chatRoomIdField.setAccessible(true);
            chatRoomIdField.set(request, chatRoomId);

            var contentField = ChatMessageRequest.class.getDeclaredField("content");
            contentField.setAccessible(true);
            contentField.set(request, content);
            
            return request;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ChatMessageRequest", e);
        }
    }
}