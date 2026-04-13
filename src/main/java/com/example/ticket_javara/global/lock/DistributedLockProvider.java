package com.example.ticket_javara.global.lock;

/**
 * 분산락 공통 인터페이스
 * 필수: LettuceDistributedLock (SETNX)
 * 도전: RedissonDistributedLock
 * 참고: CLAUDE.md §분산락 공통 모듈
 */
public interface DistributedLockProvider {

    /**
     * 락 획득 시도
     * @param key      락 키 (예: lock:seat:{eventId}:{seatId})
     * @param value    UUID (본인 락 식별용)
     * @param ttlSeconds 락 TTL (초)
     * @return 획득 성공 여부
     */
    boolean tryLock(String key, String value, long ttlSeconds);

    /**
     * 락 해제 (Lua Script로 원자적 삭제 — 본인 UUID 검증)
     * @param key   락 키
     * @param value UUID (본인 확인)
     */
    void unlock(String key, String value);
}
