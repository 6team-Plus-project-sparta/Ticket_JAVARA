package com.example.ticket_javara.domain.chat.service;

import com.example.ticket_javara.domain.chat.dto.ChatMessageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub Publisher
 * 메시지를 "chat-room:{chatRoomId}" 채널에 발행하여
 * 다중 서버 환경에서도 모든 인스턴스의 구독자에게 전달.
 */
@Slf4j
@Service
public class RedisChatPublisher {

    private static final String CHANNEL_PREFIX = "chat-room:";

    private final RedisTemplate<String, Object> chatRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatPublisher(
            @Qualifier("chatRedisTemplate") RedisTemplate<String, Object> chatRedisTemplate,
            @Qualifier("chatObjectMapper") ObjectMapper objectMapper) {
        this.chatRedisTemplate = chatRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(Long chatRoomId, ChatMessageResponse message) {
        String channel = CHANNEL_PREFIX + chatRoomId;
        try {
            String json = objectMapper.writeValueAsString(message);
            chatRedisTemplate.convertAndSend(channel, json);
            log.debug("[RedisChatPublisher] 메시지 발행 - channel: {}", channel);
        } catch (JsonProcessingException e) {
            log.error("[RedisChatPublisher] 직렬화 실패 - channel: {}, error: {}", channel, e.getMessage());
        }
    }
}
