package com.example.ticket_javara.domain.coupon.service;

import com.example.ticket_javara.domain.coupon.dto.CreateCouponRequest;
import com.example.ticket_javara.domain.coupon.dto.CreateCouponResponse;
import com.example.ticket_javara.domain.coupon.dto.IssueCouponResponse;
import com.example.ticket_javara.domain.coupon.entity.Coupon;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.repository.CouponRepository;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.NotFoundException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String REDIS_METRICS_KEY_PREFIX = "coupon:metrics:";
    
    // TTL 설정: 쿠폰 만료일까지 + 7일 여유분
    private static final long DEFAULT_TTL_DAYS = 7;

    @Transactional
    public CreateCouponResponse createCoupon(CreateCouponRequest request) {
        // Coupon 생성
        Coupon coupon = Coupon.builder()
                .name(request.getName())
                .discountAmount(request.getDiscountAmount())
                .totalQuantity(request.getTotalQuantity())
                .startAt(request.getStartAt())
                .expiredAt(request.getExpiredAt())
                .imageUrl(request.getImageUrl())
                .build();

        Coupon savedCoupon = couponRepository.save(coupon);

        // Redis 재고 세팅 with TTL
        String redisKey = REDIS_STOCK_KEY_PREFIX + savedCoupon.getCouponId();
        long ttlSeconds = calculateTtlSeconds(savedCoupon.getExpiredAt());
        
        stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(savedCoupon.getTotalQuantity()));
        stringRedisTemplate.expire(redisKey, java.time.Duration.ofSeconds(ttlSeconds));
        
        // 메트릭 초기화
        initializeCouponMetrics(savedCoupon.getCouponId(), ttlSeconds);
        
        log.info("쿠폰 생성 완료 - ID: {}, Redis TTL: {}초", savedCoupon.getCouponId(), ttlSeconds);

        return CreateCouponResponse.from(savedCoupon);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<com.example.ticket_javara.domain.coupon.dto.GetCouponResponse> getAllCoupons(org.springframework.data.domain.Pageable pageable) {
        return couponRepository.findAllByOrderByCouponIdDesc(pageable)
                .map(com.example.ticket_javara.domain.coupon.dto.GetCouponResponse::from);
    }

    @Transactional
    public IssueCouponResponse issueCoupon(Long userId, Long couponId) {

        // 1. 발급 전 선행 체크 (중복 & 발급 시간)
        // Spring Data JPA 명명 규칙 반영: existsByUserUserIdAndCouponCouponId 사용
        if (userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (coupon.isNotStarted(now)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_STARTED);
        }
        if (coupon.isExpired(now)) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (coupon.getRemainingQuantity() <= 0) {
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }

        // 2. 재고 차감 시도
        String redisKey = REDIS_STOCK_KEY_PREFIX + couponId;
        boolean isSuccess = decrementStockInRedis(redisKey, couponId);

        if (!isSuccess) {
            // Redis 에러 발생 -> DB Fallback (비관적 락으로 재고 차감)
            coupon = decrementStockInDbFallback(couponId);
            // DB Fallback 사용 메트릭 기록
            recordFallbackUsage(couponId);
        } else {
            // Redis DECR 성공 -> MySQL remaining_quantity 동기화 처리
            coupon.decreaseRemainingQuantity();
            // 성공 메트릭 기록
            recordSuccessfulIssue(couponId);
        }

        // 3. 발급 완료
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        UserCoupon userCoupon = UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .build();

        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);

        return IssueCouponResponse.from(savedUserCoupon, "쿠폰이 발급되었습니다!");
    }

    /**
     * Redis Lua Script 원자적 차감
     * 
     * @return true - 1 감소됨, 예외 발생 시 false 반환
     *         0을 반환하거나 RuntimeException 던질 때 대응
     */
    private boolean decrementStockInRedis(String key, Long couponId) {
        String scriptText = "local exists = redis.call('EXISTS', KEYS[1]) \n" +
                "if exists == 0 then \n" +
                "   return nil \n" +
                "end \n" +
                "local stock = redis.call('DECR', KEYS[1]) \n" +
                "if stock < 0 then \n" +
                "   redis.call('INCR', KEYS[1]) \n" +
                "   return -1 \n" +
                "end \n" +
                "return stock";

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(scriptText);
        redisScript.setResultType(Long.class);

        try {
            Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(key));
            if (result == null) {
                log.warn("Redis 키 없음 - 쿠폰 ID: {}, DB Fallback 진행", couponId);
                return false; // key 없음 -> DB Fallback으로 가서 Redis 키를 다시 살려야 함 (정상)
            }
            if (result == -1) {
                // 재고 소진은 DB에 물어볼 필요가 없습니다. 즉시 차단합니다.
                log.info("쿠폰 재고 소진 - 쿠폰 ID: {}", couponId);
                throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
            }
            log.debug("Redis 재고 차감 성공 - 쿠폰 ID: {}, 남은 재고: {}", couponId, result);
            return true;
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Redis 차감 실패(네트워크 등)로 Fallback 진입 - 쿠폰 ID: {}, 에러: {}", couponId, e.getMessage());
            return false; // Redis 장애 시 -> DB Fallback 진행 (정상)
        }
    }

    /**
     * Redis 장애 시 DB에 접근하여 비관적 락 기반 재고 차감.
     */
    private Coupon decrementStockInDbFallback(Long couponId) {
        log.warn("Executing Database Fallback for Coupon ID: {}", couponId);
        Coupon lockedCoupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (lockedCoupon.isNotStarted(now)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_STARTED);
        }
        if (lockedCoupon.isExpired(now)) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        
        if (lockedCoupon.getRemainingQuantity() <= 0) {
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }

        lockedCoupon.decreaseRemainingQuantity();
        return lockedCoupon;
    }
    
    /**
     * 쿠폰 만료일 기준 TTL 계산 (만료일 + 7일 여유분)
     */
    private long calculateTtlSeconds(LocalDateTime expiredAt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime ttlTarget = expiredAt.plusDays(DEFAULT_TTL_DAYS);
        
        long ttlSeconds = java.time.Duration.between(now, ttlTarget).getSeconds();
        
        // 최소 1시간, 최대 1년으로 제한
        return Math.max(3600, Math.min(ttlSeconds, 365 * 24 * 3600));
    }
    
    /**
     * 쿠폰 메트릭 초기화
     */
    private void initializeCouponMetrics(Long couponId, long ttlSeconds) {
        String metricsKey = REDIS_METRICS_KEY_PREFIX + couponId;
        
        // 메트릭 해시 초기화
        stringRedisTemplate.opsForHash().put(metricsKey, "total_attempts", "0");
        stringRedisTemplate.opsForHash().put(metricsKey, "redis_success", "0");
        stringRedisTemplate.opsForHash().put(metricsKey, "db_fallback", "0");
        stringRedisTemplate.opsForHash().put(metricsKey, "created_at", String.valueOf(System.currentTimeMillis()));
        
        // 메트릭 키도 동일한 TTL 적용
        stringRedisTemplate.expire(metricsKey, java.time.Duration.ofSeconds(ttlSeconds));
        
        log.debug("쿠폰 메트릭 초기화 완료 - 쿠폰 ID: {}", couponId);
    }
    
    /**
     * Redis 성공 메트릭 기록
     */
    private void recordSuccessfulIssue(Long couponId) {
        String metricsKey = REDIS_METRICS_KEY_PREFIX + couponId;
        
        try {
            stringRedisTemplate.opsForHash().increment(metricsKey, "total_attempts", 1);
            stringRedisTemplate.opsForHash().increment(metricsKey, "redis_success", 1);
        } catch (Exception e) {
            log.warn("메트릭 기록 실패 - 쿠폰 ID: {}, 에러: {}", couponId, e.getMessage());
        }
    }
    
    /**
     * DB Fallback 사용 메트릭 기록
     */
    private void recordFallbackUsage(Long couponId) {
        String metricsKey = REDIS_METRICS_KEY_PREFIX + couponId;
        
        try {
            stringRedisTemplate.opsForHash().increment(metricsKey, "total_attempts", 1);
            stringRedisTemplate.opsForHash().increment(metricsKey, "db_fallback", 1);
            
            log.warn("DB Fallback 사용됨 - 쿠폰 ID: {}", couponId);
        } catch (Exception e) {
            log.warn("메트릭 기록 실패 - 쿠폰 ID: {}, 에러: {}", couponId, e.getMessage());
        }
    }
    
    /**
     * 쿠폰 발급 통계 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getCouponMetrics(Long couponId) {
        String metricsKey = REDIS_METRICS_KEY_PREFIX + couponId;
        
        try {
            java.util.Map<Object, Object> rawMetrics = stringRedisTemplate.opsForHash().entries(metricsKey);
            
            if (rawMetrics.isEmpty()) {
                return java.util.Map.of("error", "메트릭 데이터가 없습니다.");
            }
            
            long totalAttempts = Long.parseLong((String) rawMetrics.getOrDefault("total_attempts", "0"));
            long redisSuccess = Long.parseLong((String) rawMetrics.getOrDefault("redis_success", "0"));
            long dbFallback = Long.parseLong((String) rawMetrics.getOrDefault("db_fallback", "0"));
            
            double successRate = totalAttempts > 0 ? (double) redisSuccess / totalAttempts * 100 : 0.0;
            double fallbackRate = totalAttempts > 0 ? (double) dbFallback / totalAttempts * 100 : 0.0;
            
            return java.util.Map.of(
                "couponId", couponId,
                "totalAttempts", totalAttempts,
                "redisSuccess", redisSuccess,
                "dbFallback", dbFallback,
                "successRate", String.format("%.2f%%", successRate),
                "fallbackRate", String.format("%.2f%%", fallbackRate),
                "createdAt", rawMetrics.getOrDefault("created_at", "unknown")
            );
            
        } catch (Exception e) {
            log.error("메트릭 조회 실패 - 쿠폰 ID: {}, 에러: {}", couponId, e.getMessage());
            return java.util.Map.of("error", "메트릭 조회 중 오류가 발생했습니다.");
        }
    }
}
