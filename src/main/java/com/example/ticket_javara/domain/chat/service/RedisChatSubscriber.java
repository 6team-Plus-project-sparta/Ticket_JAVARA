package com.example.ticket_javara.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Redis Pub/Sub Subscriber
 * Redis 채널로부터 메시지를 수신하여 STOMP 구독자에게 브로드캐스트.
 * PatternTopic("chat-room:*")으로 동적으로 모든 채팅방 채널 처리.
 *
 * ※ ChatMessageResponse 클래스로 역직렬화하지 않고 Map으로 처리:
 *    @NoArgsConstructor(access = PROTECTED) 로 인해 Jackson 역직렬화 실패하므로
 *    Map<String, Object>로 받아 STOMP 에 그대로 전달.
 */
@Slf4j
@Service
public class RedisChatSubscriber {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatSubscriber(
            SimpMessagingTemplate messagingTemplate,
            @Qualifier("chatObjectMapper") ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Redis 채널에서 메시지 수신 시 호출.
     * MessageListenerAdapter 요구 시그니처: (String message, String channel)
     *
     * @param message JSON 직렬화된 ChatMessageResponse
     * @param channel "chat-room:{chatRoomId}" 형식의 채널명
     */
    @SuppressWarnings("unchecked")
    public void onMessage(String message, String channel) {
        try {
            log.debug("[RedisChatSubscriber] 수신 - channel: {}, message: {}", channel, message);

            // ChatMessageResponse 대신 Map으로 역직렬화 → protected 생성자 문제 완전 우회
            Map<String, Object> msgMap = objectMapper.readValue(message, Map.class);

            // "chat-room:42" → "/sub/chat/room/42"
            String chatRoomId = channel.replace("chat-room:", "");
            String destination = "/sub/chat/room/" + chatRoomId;

            messagingTemplate.convertAndSend(destination, msgMap);
            log.debug("[RedisChatSubscriber] 브로드캐스트 완료 - destination: {}", destination);

        } catch (Exception e) {
            log.error("[RedisChatSubscriber] 메시지 처리 실패 - channel: {}, error: {}",
                    channel, e.getMessage(), e);
        }
    }
}
