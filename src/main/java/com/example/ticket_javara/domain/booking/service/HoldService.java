package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.response.HoldResponseDto;
import com.example.ticket_javara.domain.booking.repository.ActiveBookingRepository;
import com.example.ticket_javara.global.exception.ConflictException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Hold 비즈니스 로직 서비스 (FN-SEAT-02)
 *
 * ⚠️ 이 서비스는 반드시 HoldLockFacade를 통해 호출해야 한다.
 *    분산락(SETNX)은 Facade에서 획득하므로, 이 서비스는 락 내부 로직만 담당한다.
 *
 * 처리 순서 (동시성 제어 설계서 §3 시나리오 A 기준):
 *   1. user-hold-count 어뷰징 체크 (4석 초과 → 즉시 거부)
 *   2. ACTIVE_BOOKING 확인 (이미 예매 확정된 좌석 → 409)
 *   3. hold 키 존재 확인 (이미 선점된 좌석 → 409)
 *   4. hold 키 SET (5분 TTL)
 *   5. holdToken 역조회 키 SET (5분 TTL)
 *   6. user-hold-count INCR (TTL 300초 재설정)
 *
 * Redis 키 구조 (ADR-013):
 *   SET hold:{eventId}:{seatId}       = {userId}                     EX 300
 *   SET holdToken:{uuid}              = "{eventId}:{seatId}:{userId}" EX 300
 *   INCR user-hold-count:{userId}                                     EX 300
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldService {

    private final StringRedisTemplate redisTemplate;
    private final ActiveBookingRepository activeBookingRepository;

    /** Hold / holdToken TTL: 5분 */
    private static final long HOLD_TTL_SECONDS = 300L;
    /** Hold 최대 허용 수: 4석 */
    private static final int MAX_HOLD_COUNT = 4;

    /**
     * 좌석 Hold 처리 — 분산락 내부에서 실행
     * HoldLockFacade.holdSeat() → 이 메서드 순서로만 호출
     */
    @Transactional
    public HoldResponseDto processHold(Long eventId, Long seatId, Long userId) {

        // ── 1. 어뷰징 체크: user-hold-count >= 4 이면 즉시 거부 ──
        String holdCountKey = "user-hold-count:" + userId;
        String countStr = redisTemplate.opsForValue().get(holdCountKey);
        if (countStr != null && Integer.parseInt(countStr) >= MAX_HOLD_COUNT) {
            log.warn("[HoldService] Hold 수 초과 userId={} count={}", userId, countStr);
            throw new InvalidRequestException(ErrorCode.HOLD_LIMIT_EXCEEDED);
        }

        // ── 2. CONFIRMED 여부 확인 (ACTIVE_BOOKING 테이블 조회) ──
        if (activeBookingRepository.existsBySeatId(seatId)) {
            log.warn("[HoldService] 이미 예매 확정된 좌석 seatId={}", seatId);
            throw new ConflictException(ErrorCode.SEAT_ALREADY_CONFIRMED);
        }

        // ── 3. ON_HOLD 여부 확인: hold:{eventId}:{seatId} 키 존재 ──
        String holdKey = "hold:" + eventId + ":" + seatId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(holdKey))) {
            log.warn("[HoldService] 이미 선점된 좌석 eventId={}, seatId={}", eventId, seatId);
            throw new ConflictException(ErrorCode.SEAT_ALREADY_HELD);
        }

        // ── 4. Redis SET: hold:{eventId}:{seatId} = {userId} EX 300 ──
        redisTemplate.opsForValue().set(holdKey, String.valueOf(userId),
                Duration.ofSeconds(HOLD_TTL_SECONDS));

        // ── 5. holdToken 생성 + 역조회 키 SET ──
        String holdToken = UUID.randomUUID().toString();
        String tokenKey = "holdToken:" + holdToken;
        String tokenValue = eventId + ":" + seatId + ":" + userId;
        redisTemplate.opsForValue().set(tokenKey, tokenValue,
                Duration.ofSeconds(HOLD_TTL_SECONDS));

        // ── 6. user-hold-count INCR + TTL 재설정 ──
        // 키가 없으면 INCR 후 EXPIRE 설정, 있으면 INCR만 (TTL은 유지)
        redisTemplate.opsForValue().increment(holdCountKey);
        if (countStr == null) {
            // 최초 hold — TTL 초기화
            redisTemplate.expire(holdCountKey, Duration.ofSeconds(HOLD_TTL_SECONDS));
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(HOLD_TTL_SECONDS);
        log.info("[HoldService] Hold 성공 eventId={}, seatId={}, userId={}, holdToken={}",
                eventId, seatId, userId, holdToken);

        return new HoldResponseDto(holdToken, seatId, expiresAt);
    }

    /**
     * Hold 수동 해제 (FN-SEAT-03)
     * 사용자가 결제 전 좌석 선택 취소 시 호출
     */
    @Transactional
    public void releaseHold(Long eventId, Long seatId, Long userId) {
        String holdKey = "hold:" + eventId + ":" + seatId;

        // Hold 존재 확인
        String holdOwner = redisTemplate.opsForValue().get(holdKey);
        if (holdOwner == null) {
            throw new com.example.ticket_javara.global.exception.NotFoundException(
                    ErrorCode.HOLD_NOT_FOUND);
        }
        // 본인 소유 확인
        if (!holdOwner.equals(String.valueOf(userId))) {
            throw new com.example.ticket_javara.global.exception.ForbiddenException(
                    ErrorCode.HOLD_NOT_OWNED);
        }

        // hold 키 삭제
        redisTemplate.delete(holdKey);

        // holdToken 역조회 키 삭제 — hold:{eventId}:{seatId} 에 저장된 값으로는 token UUID를 모르므로
        // holdToken:{uuid} → "{eventId}:{seatId}:{userId}" 패턴 삭제는 SCAN으로 처리
        // (간소화: TTL 만료로 자연 정리 허용, 또는 holdToken을 hold 값에 함께 저장하는 확장 가능)

        // user-hold-count DECR
        String holdCountKey = "user-hold-count:" + userId;
        Long newCount = redisTemplate.opsForValue().decrement(holdCountKey);
        if (newCount != null && newCount <= 0) {
            redisTemplate.delete(holdCountKey);
        }

        log.info("[HoldService] Hold 해제 eventId={}, seatId={}, userId={}", eventId, seatId, userId);
    }
}
