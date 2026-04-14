package com.example.ticket_javara.domain.booking;

import com.example.ticket_javara.domain.booking.dto.response.HoldResponseDto;
import com.example.ticket_javara.domain.booking.facade.HoldLockFacade;
import com.example.ticket_javara.domain.booking.repository.ActiveBookingRepository;
import com.example.ticket_javara.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좌석 Hold 동시성 테스트 (동시성 제어 설계서 §5-1)
 *
 * 시나리오 A: 100개 Thread가 동시에 같은 좌석에 Hold 요청
 * 기대 결과: 정확히 1명만 Hold 성공, 나머지 99명은 실패
 *
 * 테스트 조건:
 * - 실제 Redis 연결 필요 (docker-compose.yml의 Redis 컨테이너)
 * - 실제 MySQL DB 연결 필요 (ACTIVE_BOOKING 조회)
 * - @SpringBootTest: 전체 애플리케이션 컨텍스트 로드
 *
 * 실행 전 필수:
 *   docker-compose up -d mysql redis
 */
@SpringBootTest
@ActiveProfiles("test")
class HoldConcurrencyTest {

    @Autowired
    private HoldLockFacade holdLockFacade;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ActiveBookingRepository activeBookingRepository;

    /** 테스트용 고정 eventId / seatId (실제 DB에 존재해야 함) */
    private static final Long TEST_EVENT_ID = 1L;
    private static final Long TEST_SEAT_ID  = 1L;

    @BeforeEach
    void setUp() {
        // 이전 테스트 Hold 키 정리
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        cleanupTestKeys();
    }

    /**
     * 시나리오 A: 100명 동시 Hold 요청 → 1명만 성공
     *
     * CyclicBarrier: 모든 스레드가 준비된 후 동시에 출발 (진정한 Race Condition 재현)
     * CountDownLatch: 모든 스레드 완료 대기
     * AtomicInteger: 스레드 안전한 카운터
     */
    @Test
    @DisplayName("[동시성] 100명이 동시에 같은 좌석 Hold 시 1명만 성공해야 한다")
    void concurrentHoldTest() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);  // 동시 출발 동기화
        CountDownLatch latch = new CountDownLatch(threadCount);  // 전체 완료 대기

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        AtomicInteger lockFailed   = new AtomicInteger(0);
        AtomicInteger alreadyHeld  = new AtomicInteger(0);
        AtomicInteger limitExceeded = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1L;  // 각 스레드는 다른 userId 사용
            executor.submit(() -> {
                try {
                    // CyclicBarrier: 모든 스레드가 여기까지 도달해야 동시에 출발
                    barrier.await();

                    HoldResponseDto response = holdLockFacade.holdSeat(
                            TEST_EVENT_ID, TEST_SEAT_ID, userId);
                    if (response != null) {
                        successCount.incrementAndGet();
                    }
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                    String code = e.getErrorCode().getCode();
                    if ("S002".equals(code)) lockFailed.incrementAndGet();        // SEAT_LOCK_FAILED
                    else if ("S003".equals(code)) alreadyHeld.incrementAndGet();  // SEAT_ALREADY_HELD
                    else if ("S001".equals(code)) limitExceeded.incrementAndGet(); // HOLD_LIMIT_EXCEEDED
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기 (최대 30초)
        latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // then
        System.out.println("=== 동시성 테스트 결과 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("실패 합계: " + failCount.get());
        System.out.println("  └ SEAT_LOCK_FAILED (분산락 실패): " + lockFailed.get());
        System.out.println("  └ SEAT_ALREADY_HELD (선점됨): " + alreadyHeld.get());
        System.out.println("  └ HOLD_LIMIT_EXCEEDED (4석 초과): " + limitExceeded.get());

        // ⭐ 핵심 검증: 정확히 1명만 성공
        assertThat(successCount.get())
                .as("100명 중 정확히 1명만 Hold 성공해야 한다")
                .isEqualTo(1);

        assertThat(failCount.get())
                .as("나머지 99명은 실패해야 한다")
                .isEqualTo(99);

        // Hold 키가 Redis에 정확히 1개 존재
        Boolean holdKeyExists = redisTemplate.hasKey("hold:" + TEST_EVENT_ID + ":" + TEST_SEAT_ID);
        assertThat(holdKeyExists)
                .as("Redis에 hold 키가 정확히 1개 존재해야 한다")
                .isTrue();
    }

    /**
     * 시나리오 H: 같은 사용자가 동일 좌석에 2번 Hold 요청
     * 첫 번째 성공 후 두 번째는 분산락 또는 hold 키 존재로 차단되어야 함
     */
    @Test
    @DisplayName("[동시성] 같은 사용자가 동일 좌석에 중복 Hold 시 1번만 성공해야 한다")
    void duplicateHoldSameUserTest() throws InterruptedException {
        // given
        long userId = 999L;
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    holdLockFacade.holdSeat(TEST_EVENT_ID, TEST_SEAT_ID, userId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
    }

    /** 테스트 Redis 키 정리 */
    private void cleanupTestKeys() {
        String holdKey   = "hold:" + TEST_EVENT_ID + ":" + TEST_SEAT_ID;
        String lockKey   = "lock:seat:" + TEST_EVENT_ID + ":" + TEST_SEAT_ID;

        redisTemplate.delete(holdKey);
        redisTemplate.delete(lockKey);

        // user-hold-count 정리 (userId 1~100)
        for (int i = 1; i <= 100; i++) {
            redisTemplate.delete("user-hold-count:" + i);
        }
        redisTemplate.delete("user-hold-count:999");
    }
}
