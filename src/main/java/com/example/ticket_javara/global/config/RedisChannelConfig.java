package com.example.ticket_javara.global.config;

import com.example.ticket_javara.domain.chat.service.RedisChatSubscriber;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Pub/Sub 채팅 채널 설정
 * - chat-room:* 패턴으로 모든 채팅방 채널을 동적으로 구독
 * - 기존 StringRedisTemplate(쿠폰/캐시용)과 별도로 채팅 전용 RedisTemplate 구성
 */
@Configuration
@RequiredArgsConstructor
public class RedisChannelConfig {

    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * 채팅 메시지 전용 RedisTemplate (String 직렬화)
     * 기존 StringRedisTemplate과 다른 빈 이름으로 분리.
     */
    @Bean(name = "chatRedisTemplate")
    public RedisTemplate<String, Object> chatRedisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    /**
     * 채팅 메시지 ObjectMapper — LocalDateTime 직렬화 지원
     */
    @Bean(name = "chatObjectMapper")
    public ObjectMapper chatObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Redis 채널 구독 메시지 리스너 어댑터
     * - StringRedisSerializer 명시 설정: Publisher가 String으로 발행하므로 Subscriber도 String으로 파싱
     * - 설정 누락 시 기본값 JdkSerializationRedisSerializer가 적용되어 JSON 역직렬화 실패
     */
    @Bean
    public MessageListenerAdapter chatMessageListenerAdapter(RedisChatSubscriber subscriber) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "onMessage");
        adapter.setSerializer(new StringRedisSerializer());  // ★ 핵심: String 직렬화로 명시 지정
        return adapter;
    }

    /**
     * Redis 메시지 리스너 컨테이너
     * "chat-room:*" 패턴으로 모든 채팅방 채널을 동적 구독
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            MessageListenerAdapter chatMessageListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        // chat-room:* 패턴으로 채팅방 채널 전체 구독 (채널 = "chat-room:{chatRoomId}")
        container.addMessageListener(chatMessageListenerAdapter, new PatternTopic("chat-room:*"));
        return container;
    }
}
