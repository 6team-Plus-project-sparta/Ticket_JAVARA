package com.example.ticket_javara.global.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Lettuce SETNX 기반 분산락 구현체 (필수)
 * - lock.provider=lettuce 일 때 활성화 (matchIfMissing=true 이므로 기본값)
 * - tryLock: SETNX + TTL 설정 (원자적)
 * - unlock: Lua Script로 UUID 검증 후 DEL (본인 락만 해제)
 * 참고: CLAUDE.md §분산락 공통 모듈, 09_동시성_제어_설계서.md
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lock.provider", havingValue = "lettuce", matchIfMissing = true)
public class LettuceDistributedLock implements DistributedLockProvider {

    private final StringRedisTemplate redisTemplate;

    // UUID 검증 후 DEL — 본인 락만 해제 (원자적)
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            LuaScripts.UNLOCK_SCRIPT, Long.class
    );

    public LettuceDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 락 획득 시도 (Fail Fast — 재시도 없음)
     * SETNX(SET if Not eXist) + EX(TTL) 원자적 설정
     */
    @Override
    public boolean tryLock(String key, String value, long ttlSeconds) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));
        boolean acquired = Boolean.TRUE.equals(result);
        log.debug("[LettuceDistributedLock] tryLock key={}, acquired={}", key, acquired);
        return acquired;
    }

    /**
     * 락 해제 — Lua Script로 UUID 검증 후 DEL
     * 본인 UUID와 다른 경우 삭제하지 않음 (안전장치)
     */
    @Override
    public void unlock(String key, String value) {
        Long result = redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), value);
        if (result == null || result == 0L) {
            log.warn("[LettuceDistributedLock] 락 해제 실패 또는 이미 만료 key={}", key);
        } else {
            log.debug("[LettuceDistributedLock] 락 해제 성공 key={}", key);
        }
    }
}
