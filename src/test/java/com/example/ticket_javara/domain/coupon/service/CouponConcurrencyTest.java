package com.example.ticket_javara.domain.coupon.service;

import com.example.ticket_javara.domain.coupon.dto.CreateCouponRequest;
import com.example.ticket_javara.domain.coupon.dto.CreateCouponResponse;
import com.example.ticket_javara.domain.coupon.entity.Coupon;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.entity.UserCouponStatus;
import com.example.ticket_javara.domain.coupon.repository.CouponRepository;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class CouponConcurrencyTest {

    @Autowired
    private CouponService couponService;
    
    @Autowired
    private CouponRepository couponRepository;
    
    @Autowired
    private UserCouponRepository userCouponRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Coupon testCoupon;
    private List<User> testUsers;

    @BeforeEach
    void setUp() {
        // Redis 키 정리
        stringRedisTemplate.getConnectionFactory().getConnection().flushAll();
        
        // 테스트 쿠폰 생성 - Builder 패턴 사용
        CreateCouponRequest request = CreateCouponRequest.builder()
                .name("동시성 테스트 쿠폰")
                .discountAmount(5000)
                .totalQuantity(10)
                .startAt(LocalDateTime.now().minusHours(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();
        
        CreateCouponResponse response = couponService.createCoupon(request);
        testCoupon = couponRepository.findById(response.getCouponId()).orElseThrow();
        
        // 테스트 사용자들 생성 (쿠폰 수량보다 많이)
        testUsers = IntStream.range(1, 21) // 20명의 사용자
                .mapToObj(i -> User.builder()
                        .email("test" + i + "@example.com")
                        .password("password")
                        .nickname("테스터" + i)
                        .role(UserRole.USER)
                        .build())
                .map(userRepository::save)
                .toList();
    }

    @AfterEach
    void tearDown() {
        // Redis 정리
        stringRedisTemplate.getConnectionFactory().getConnection().flushAll();
        
        // DB 정리 (외래키 제약으로 인해 순서 중요)
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("동시에 20명이 10장 제한 쿠폰 발급 요청 - 정확히 10명만 성공해야 함")
    void concurrentCouponIssue_ShouldIssueExactQuantity() throws Exception {
        // Given
        int threadCount = 20;
        int couponQuantity = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When - 동시에 쿠폰 발급 요청
        List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        couponService.issueCoupon(testUsers.get(i).getUserId(), testCoupon.getCouponId());
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.COUPON_EXHAUSTED) {
                            failCount.incrementAndGet();
                        } else {
                            throw e; // 예상치 못한 에러는 다시 던짐
                        }
                    }
                }, executorService))
                .toList();

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        // awaitility를 사용하여 DB에 정확히 10개의 쿠폰이 발급될 때까지 대기
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<UserCoupon> issuedCoupons = userCouponRepository.findAll().stream()
                            .filter(uc -> uc.getCoupon().getCouponId().equals(testCoupon.getCouponId()))
                            .toList();
                    assertThat(issuedCoupons).hasSize(couponQuantity);
                });

        // Then
        assertThat(successCount.get()).isEqualTo(couponQuantity);
        assertThat(failCount.get()).isEqualTo(threadCount - couponQuantity);
        
        // DB 검증 - @Query를 사용하여 직접 조회
        List<UserCoupon> issuedCoupons = userCouponRepository.findAll().stream()
                .filter(uc -> uc.getCoupon().getCouponId().equals(testCoupon.getCouponId()))
                .toList();
        assertThat(issuedCoupons).hasSize(couponQuantity);
        assertThat(issuedCoupons).allMatch(uc -> uc.getStatus() == UserCouponStatus.ISSUED);
        
        // MySQL 재고 검증 - 문서 명세에 따라 Redis와 동기화되어야 함
        Coupon updatedCoupon = couponRepository.findById(testCoupon.getCouponId()).orElseThrow();
        assertThat(updatedCoupon.getRemainingQuantity()).isEqualTo(0);
        
        // Redis 재고 검증 - 0이어야 함 (모든 재고 소진)
        String redisKey = "coupon:stock:" + testCoupon.getCouponId();
        String remainingStock = stringRedisTemplate.opsForValue().get(redisKey);
        assertThat(remainingStock).isEqualTo("0");
    }

    @Test
    @DisplayName("Redis 장애 시 DB Fallback 동작 검증")
    void dbFallback_WhenRedisUnavailable() throws Exception {
        // Given - Redis 키 삭제로 장애 상황 시뮬레이션
        String redisKey = "coupon:stock:" + testCoupon.getCouponId();
        stringRedisTemplate.delete(redisKey);
        
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);

        // When - 동시에 쿠폰 발급 요청 (Redis 키 없는 상태)
        List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        couponService.issueCoupon(testUsers.get(i).getUserId(), testCoupon.getCouponId());
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        // 재고 부족 외의 에러는 실패로 간주
                        if (e.getErrorCode() != ErrorCode.COUPON_EXHAUSTED) {
                            throw e;
                        }
                    }
                }, executorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        // Then - DB Fallback으로도 정상 발급되어야 함
        assertThat(successCount.get()).isGreaterThan(0);
        
        List<UserCoupon> issuedCoupons = userCouponRepository.findAll().stream()
                .filter(uc -> uc.getCoupon().getCouponId().equals(testCoupon.getCouponId()))
                .toList();
        assertThat(issuedCoupons).hasSize(successCount.get());
        
        // 메트릭에서 DB Fallback 사용 확인
        var metrics = couponService.getCouponMetrics(testCoupon.getCouponId());
        assertThat(metrics.get("dbFallback")).isNotEqualTo(0L);
    }

    @Test
    @DisplayName("중복 발급 방지 검증 - 동일 사용자가 여러 번 요청")
    void preventDuplicateIssue_SameUser() throws Exception {
        // Given
        User singleUser = testUsers.get(0);
        int attemptCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(attemptCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // When - 동일 사용자가 동시에 여러 번 발급 요청
        List<CompletableFuture<Void>> futures = IntStream.range(0, attemptCount)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        couponService.issueCoupon(singleUser.getUserId(), testCoupon.getCouponId());
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == ErrorCode.COUPON_ALREADY_ISSUED) {
                            duplicateCount.incrementAndGet();
                        } else {
                            throw e;
                        }
                    } catch (Exception e) {
                        // DataIntegrityViolationException도 중복 발급으로 간주
                        if (e.getCause() instanceof org.springframework.dao.DataIntegrityViolationException ||
                            e instanceof org.springframework.dao.DataIntegrityViolationException) {
                            duplicateCount.incrementAndGet();
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                }, executorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();

        // Then - 정확히 1번만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(attemptCount - 1);
        
        List<UserCoupon> userCoupons = userCouponRepository.findByUserUserId(singleUser.getUserId());
        assertThat(userCoupons).hasSize(1);
    }

    @Test
    @DisplayName("쿠폰 발급 시간 제한 검증")
    void couponIssue_TimeRestriction() {
        // Given - 아직 시작되지 않은 쿠폰
        CreateCouponRequest futureRequest = CreateCouponRequest.builder()
                .name("미래 쿠폰")
                .discountAmount(3000)
                .totalQuantity(100)
                .startAt(LocalDateTime.now().plusDays(1)) // 내일 시작
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();
        
        CreateCouponResponse futureResponse = couponService.createCoupon(futureRequest);
        User testUser = testUsers.get(0);

        // When & Then - 시작 전 발급 시도
        assertThatThrownBy(() -> 
            couponService.issueCoupon(testUser.getUserId(), futureResponse.getCouponId())
        ).isInstanceOf(BusinessException.class)
         .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_NOT_STARTED);

        // Given - 이미 만료된 쿠폰
        CreateCouponRequest expiredRequest = CreateCouponRequest.builder()
                .name("만료된 쿠폰")
                .discountAmount(3000)
                .totalQuantity(100)
                .startAt(LocalDateTime.now().minusDays(10))
                .expiredAt(LocalDateTime.now().minusDays(1)) // 어제 만료
                .build();
        
        CreateCouponResponse expiredResponse = couponService.createCoupon(expiredRequest);

        // When & Then - 만료 후 발급 시도
        assertThatThrownBy(() -> 
            couponService.issueCoupon(testUser.getUserId(), expiredResponse.getCouponId())
        ).isInstanceOf(BusinessException.class)
         .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_EXPIRED);
    }

    @Test
    @DisplayName("Redis TTL 설정 검증")
    void redisTtl_ShouldBeSetCorrectly() {
        // Given & When - 쿠폰 생성 시 TTL 자동 설정됨 (setUp에서 실행)
        String redisKey = "coupon:stock:" + testCoupon.getCouponId();
        String metricsKey = "coupon:metrics:" + testCoupon.getCouponId();
        
        // Then - TTL이 설정되어 있어야 함
        Long stockTtl = stringRedisTemplate.getExpire(redisKey);
        Long metricsTtl = stringRedisTemplate.getExpire(metricsKey);
        
        assertThat(stockTtl).isGreaterThan(0); // TTL이 설정됨
        assertThat(metricsTtl).isGreaterThan(0); // 메트릭도 TTL 설정됨
        
        // 대략적인 TTL 검증 (30일 + 7일 여유분 = 37일 정도)
        long expectedTtl = 37 * 24 * 3600; // 37일을 초로 변환
        assertThat(stockTtl).isBetween(expectedTtl - 3600, expectedTtl); // 1시간 오차 허용
    }
}