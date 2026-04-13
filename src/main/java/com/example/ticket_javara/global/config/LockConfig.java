package com.example.ticket_javara.global.config;

import com.example.ticket_javara.global.lock.DistributedLockProvider;
import com.example.ticket_javara.global.lock.LettuceDistributedLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 분산락 Feature Flag 설정 — ADR-002
 *
 * lock.provider=lettuce (기본값) → LettuceDistributedLock 활성화
 * lock.provider=redisson         → RedissonDistributedLock 활성화 (도전 기능)
 *
 * 전환 방법: application-local.yml의 lock.provider 값만 변경 (코드 수정 없음)
 *
 * cache.provider / lock.provider 두 Feature Flag를 나란히 관리:
 *   cache.provider: caffeine  # caffeine | redis    (ADR-006)
 *   lock.provider:  lettuce   # lettuce  | redisson (ADR-002)
 */
@Configuration
public class LockConfig {

    /**
     * ─── Lettuce SETNX 분산락 (필수, 기본값) ───
     * lock.provider가 lettuce이거나 값이 없으면 활성화 (matchIfMissing=true)
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "lock.provider", havingValue = "lettuce", matchIfMissing = true)
    public DistributedLockProvider lettuceLockProvider(StringRedisTemplate stringRedisTemplate) {
        return new LettuceDistributedLock(stringRedisTemplate);
    }

    /*
     * ─── Redisson 분산락 (도전 기능) ───
     * lock.provider=redisson 일 때만 활성화
     *
     * 도전 구현 시 아래 주석을 해제하고 Redisson 의존성 추가:
     * build.gradle: implementation 'org.redisson:redisson-spring-boot-starter:3.27.2'
     *
     * @Bean
     * @Primary
     * @ConditionalOnProperty(name = "lock.provider", havingValue = "redisson")
     * public DistributedLockProvider redissonLockProvider(RedissonClient redissonClient) {
     *     return new RedissonDistributedLock(redissonClient);
     * }
     *
     * @Bean
     * @ConditionalOnProperty(name = "lock.provider", havingValue = "redisson")
     * public RedissonClient redissonClient(
     *         @Value("${spring.data.redis.host}") String host,
     *         @Value("${spring.data.redis.port}") int port) {
     *     Config config = new Config();
     *     config.useSingleServer().setAddress("redis://" + host + ":" + port);
     *     return Redisson.create(config);
     * }
     */
}
