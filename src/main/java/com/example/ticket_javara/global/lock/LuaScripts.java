package com.example.ticket_javara.global.lock;

/**
 * Redis Lua Script 상수 모음
 *
 * UNLOCK_SCRIPT:
 *   UUID 검증 후 DEL — 본인 락만 해제 (원자적)
 *   KEYS[1]: 락 키, ARGV[1]: UUID 값
 *
 * COUPON_DECR_SCRIPT:
 *   DECR 후 < 0 이면 INCR으로 원상복구 후 -1 반환 (음수 방어)
 *   쿠폰 선착순 발급 시 수량 초과 방지
 *   KEYS[1]: coupon:stock:{couponId}
 *
 * 참고: 09_동시성_제어_설계서.md, CLAUDE.md §분산락 공통 모듈
 */
public final class LuaScripts {

    private LuaScripts() {}

    /**
     * 분산락 해제 Lua Script
     * - 현재 값이 ARGV[1](UUID)과 일치하면 DEL 후 1 반환
     * - 불일치 시 0 반환 (본인 락이 아님)
     */
    public static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 쿠폰 수량 원자적 DECR Lua Script
     * - DECR 수행
     * - 결과 < 0 이면 INCR으로 원상복구하고 -1 반환 (소진 신호)
     * - 결과 >= 0 이면 남은 수량 반환 (차감 성공)
     */
    public static final String COUPON_DECR_SCRIPT =
            "local stock = redis.call('decr', KEYS[1]) " +
            "if stock < 0 then " +
            "    redis.call('incr', KEYS[1]) " +
            "    return -1 " +
            "end " +
            "return stock";
}
