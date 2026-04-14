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
import com.example.ticket_javara.global.exception.ForbiddenException;
import com.example.ticket_javara.global.exception.NotFoundException;
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

    @Transactional
    public CreateCouponResponse createCoupon(String userRole, CreateCouponRequest request) {
        // ADMIN 권한 검증
        if (!"ADMIN".equals(userRole)) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }

        // Coupon 생성
        Coupon coupon = Coupon.builder()
                .name(request.getName())
                .discountAmount(request.getDiscountAmount())
                .totalQuantity(request.getTotalQuantity())
                .startAt(request.getStartAt())
                .expiredAt(request.getExpiredAt())
                .build();

        Coupon savedCoupon = couponRepository.save(coupon);

        // Redis 재고 세팅
        String redisKey = REDIS_STOCK_KEY_PREFIX + savedCoupon.getCouponId();
        stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(savedCoupon.getTotalQuantity()));

        return CreateCouponResponse.from(savedCoupon);
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

        if (coupon.isNotStarted()) {
            throw new BusinessException(ErrorCode.COUPON_NOT_STARTED);
        }
        if (coupon.getRemainingQuantity() <= 0) {
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }

        // 2. 재고 차감 시도
        String redisKey = REDIS_STOCK_KEY_PREFIX + couponId;
        boolean isSuccess = decrementStockInRedis(redisKey);

        if (!isSuccess) {
            // Redis 에러 발생 -> DB Fallback (비관적 락으로 재고 차감)
            coupon = decrementStockInDbFallback(couponId);
        } else {
            // Redis DECR 성공 -> MySQL remaining_quantity 동기화 처리
            coupon.decreaseRemainingQuantity();
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
    private boolean decrementStockInRedis(String key) {
        String scriptText = "local stock = redis.call('DECR', KEYS[1]) \n" +
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
                return false; // key 없음, fallback
            }
            if (result == -1) {
                throw new BusinessException(ErrorCode.COUPON_EXHAUSTED); // 재고 없음
            }
            return true;
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Redis 차감 실패로 Fallback 진입: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Redis 장애 시 DB에 접근하여 비관적 락 기반 재고 차감.
     */
    private Coupon decrementStockInDbFallback(Long couponId) {
        log.warn("Executing Database Fallback for Coupon ID: {}", couponId);
        Coupon lockedCoupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));

        if (!lockedCoupon.isIssuable()) {
            if (lockedCoupon.getRemainingQuantity() <= 0) {
                throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
            }
            throw new BusinessException(ErrorCode.COUPON_NOT_STARTED);
        }

        lockedCoupon.decreaseRemainingQuantity();
        return lockedCoupon;
    }
}
