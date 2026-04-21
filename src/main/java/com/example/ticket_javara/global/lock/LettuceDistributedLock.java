package com.example.ticket_javara.global.lock;

import com.example.ticket_javara.global.exception.ConflictException;
import com.example.ticket_javara.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lettuce SETNX 기반 분산락 구현체 (필수)
 * - Bean 등록은 LockConfig에서 @ConditionalOnProperty로 관리 (ADR-002)
 * - tryLock: SETNX + TTL 설정 (원자적)
 * - unlock: Lua Script로 UUID 검증 후 DEL (본인 락만 해제)
 * 참고: 09_동시성_제어_설계서.md, CLAUDE.md §분산락 공통 모듈
 */
@Slf4j
public class LettuceDistributedLock implements DistributedLockProvider {

    private final StringRedisTemplate redisTemplate;

    // UUID 검증 후 DEL — 본인 락만 해제 (원자적)
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            LuaScripts.UNLOCK_SCRIPT, Long.class
    );

    private static final long DEFAULT_TTL_SECONDS = 10L;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100L;

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

    @Override
    public <T> T executeWithLock(String key, Supplier<T> task) {
        String lockValue = java.util.UUID.randomUUID().toString();
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            if (tryLock(key, lockValue, DEFAULT_TTL_SECONDS)) {
                try {
                    log.debug("[LettuceDistributedLock] 락 획득 성공, 작업 실행 key={}, attempt={}", key, attempt);
                    return task.get();
                } finally {
                    unlock(key, lockValue);
                }
            }
            
            if (attempt < MAX_RETRY_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt); // 지수 백오프
                    log.debug("[LettuceDistributedLock] 락 재시도 대기 key={}, attempt={}", key, attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException(ErrorCode.SEAT_LOCK_FAILED);
                }
            }
        }
        
        log.warn("[LettuceDistributedLock] 락 획득 최종 실패 key={}, maxAttempts={}", key, MAX_RETRY_ATTEMPTS);
        throw new ConflictException(ErrorCode.SEAT_LOCK_FAILED);
    }
}
