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
    @Bean
    @ConditionalOnProperty(name = "cache.provider", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 캐시별 TTL 개별 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("event-search", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("event-detail", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
