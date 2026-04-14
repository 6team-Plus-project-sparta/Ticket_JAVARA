package com.example.ticket_javara.domain.booking.facade;

import com.example.ticket_javara.domain.booking.dto.response.HoldResponseDto;
import com.example.ticket_javara.domain.booking.service.HoldService;
import com.example.ticket_javara.global.exception.ConflictException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.lock.DistributedLockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hold 분산락 전용 Facade — ⭐ 트랜잭션 없음 (의도적)
 *
 * [설계 이유]
 * @Transactional + Lettuce Lock 생명주기 충돌 방지:
 *   잘못된 구조: @Transactional 안에서 lock/unlock → COMMIT 전에 unlock → 다른 스레드가 미반영 DB 읽음
 *   올바른 구조:
 *     HoldLockFacade (락 담당, 트랜잭션 없음)
 *       └─ lock.tryLock()
 *       └─ holdService.processHold()  ← @Transactional (DB 처리 + COMMIT)
 *       └─ lock.unlock()              ← finally: COMMIT 이후 해제 보장
 *
 * 참고: 11_백엔드_패키지_구조_설계서.md §3-5, CLAUDE.md §HoldLockFacade 패턴
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldLockFacade {

    private final DistributedLockProvider lockProvider;
    private final HoldService holdService;

    /** 분산락 TTL: 3초 (통상 DB 트랜잭션 500ms 기준 여유 6배) */
    private static final long LOCK_TTL_SECONDS = 3L;

    /**
     * 좌석 Hold 획득 (Fail Fast 전략)
     * 1. Lettuce SETNX 분산락 획득 시도 — 실패 시 즉시 409
     * 2. HoldService.processHold() 호출 (@Transactional, DB 처리)
     * 3. finally: Lua Script로 본인 락만 원자적 해제 (COMMIT 이후 보장)
     *
     * @param eventId 이벤트 ID
     * @param seatId  좌석 ID
     * @param userId  현재 사용자 ID
     * @return HoldResponseDto { holdToken, seatId, expiresAt }
     */
    public HoldResponseDto holdSeat(Long eventId, Long seatId, Long userId) {
        // 락 키: lock:seat:{eventId}:{seatId}
        String lockKey = "lock:seat:" + eventId + ":" + seatId;
        // 락 값: UUID — 본인 락만 해제하기 위한 식별자
        String lockValue = UUID.randomUUID().toString();

        // ① 분산락 획득 (Fail Fast — waitTime=0)
        boolean acquired = lockProvider.tryLock(lockKey, lockValue, LOCK_TTL_SECONDS);
        if (!acquired) {
            log.warn("[HoldLockFacade] 분산락 획득 실패 eventId={}, seatId={}, userId={}",
                    eventId, seatId, userId);
            throw new ConflictException(ErrorCode.SEAT_LOCK_FAILED);
        }

        try {
            // ② 비즈니스 로직 실행 (@Transactional — DB 처리 + COMMIT)
            return holdService.processHold(eventId, seatId, userId);
        } finally {
            // ③ COMMIT 이후 락 해제 (Lua Script — UUID 검증 후 원자적 DEL)
            lockProvider.unlock(lockKey, lockValue);
            log.debug("[HoldLockFacade] 분산락 해제 eventId={}, seatId={}", eventId, seatId);
        }
    }
}
