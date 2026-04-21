package com.example.ticket_javara.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 캐시 설정 — 3단계 진화 흐름
 *
 * cache.provider=caffeine (기본값): 필수 구현
 *   - event-search TTL 5분, event-detail TTL 10분
 *
 * cache.provider=redis (도전): Cache-Aside 패턴으로 교체
 *   - 동일 TTL, JSON 직렬화
 *
 * 전환 방식: application-local.yml의 cache.provider 값 변경만으로 코드 수정 없이 전환 가능 (ADR-006)
 * 참고: CLAUDE.md §캐싱 전략
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * ───── Caffeine 캐시 매니저 (필수, 기본값) ─────
     * lock.provider가 caffeine이거나 값이 없으면 활성화
     */
    @Bean
    @ConditionalOnProperty(name = "cache.provider", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // 이벤트 검색 결과 캐시: TTL 5분
        manager.registerCustomCache("event-search",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build());

        // 이벤트 상세 조회 캐시: TTL 10분
        manager.registerCustomCache("event-detail",
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .build());

        return manager;
    }

    /**
     * ───── Redis 캐시 매니저 (도전) ─────
     * cache.provider=redis 일 때만 활성화
     */
    @ConditionalOnProperty(name = "cache.provider", havingValue = "redis")
    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {

        // ObjectMapper — JavaTimeModule 등록 (LocalDateTime 직렬화)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
            objectMapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL
        );

        // Value 직렬화: JSON
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(objectMapper);

        // ✅ event-search: TTL 5분 (기본값)
        RedisCacheConfiguration searchConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(jsonSerializer)
            )
            .disableCachingNullValues();

        // ✅ event-detail: TTL 10분 (SA 문서 FN-EVT-03 기준) — BUG-02 수정
        RedisCacheConfiguration detailConfig = searchConfig
            .entryTtl(Duration.ofMinutes(10));

        // 캐시별 TTL 명시 (Caffeine 모드와 동일한 TTL 정책)
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("event-search", searchConfig);
        cacheConfigs.put("event-detail", detailConfig);

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(searchConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }

}
