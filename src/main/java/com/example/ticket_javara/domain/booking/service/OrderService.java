package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.OrderCreateRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.OrderResponseDto;
import com.example.ticket_javara.domain.booking.entity.Booking;
import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.repository.BookingRepository;
import com.example.ticket_javara.domain.booking.repository.OrderRepository;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.entity.UserCouponStatus;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.entity.Seat;
import com.example.ticket_javara.domain.event.repository.SeatRepository;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.*;
import com.example.ticket_javara.global.lock.DistributedLockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 주문 생성 서비스 (FN-BK-01)
 *
 * 처리 순서:
 *   1. holdToken 역조회 → (eventId, seatId, userId) 파싱
 *   2. hold 키 소유자 재검증
 *   3. [쿠폰 적용 시] 쿠폰 유효성 검증
 *   4. ORDER 생성 (status=PENDING)
 *   5. BOOKING 생성 (각 좌석별, status=PENDING)
 *   6. Hold TTL 연장 (+5분, L-02 대응)
 *   7. Mock PG 결제 요청 (로그 출력으로 시뮬레이션)
 *
 * 락 획득 순서 (데드락 방지):
 *   ① 좌석 Hold 검증(Redis) → ② 쿠폰 사용 락(Redis) → ③ DB 트랜잭션
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final StringRedisTemplate redisTemplate;
    private final DistributedLockProvider lockProvider;

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final UserCouponRepository userCouponRepository;

    private static final long HOLD_TTL_SECONDS = 300L;
    private static final long COUPON_LOCK_TTL_SECONDS = 3L;

    /**
     * 주문 생성 (FN-BK-01)
     * holdTokens로 Redis Hold를 역조회하여 검증 후 ORDER / BOOKING을 생성한다.
     */
    @Transactional
    public OrderResponseDto createOrder(OrderCreateRequestDto dto, Long userId) {

        // ── 사용자 조회 ──
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        // ── ① holdToken 역조회 및 검증 ──
        // 각 holdToken에서 (eventId, seatId, userId) 파싱
        List<HoldInfo> holdInfos = resolveHoldTokens(dto.getHoldTokens(), userId);

        // ── ② [쿠폰 적용 시] 쿠폰 유효성 검증 ──
        UserCoupon userCoupon = null;
        String couponLockKey = null;
        String couponLockValue = null;

        if (dto.getUserCouponId() != null) {
            // 락 획득 순서: ① 좌석 Hold 검증(완료) → ② 쿠폰 사용 락 획득
            couponLockKey = "lock:user-coupon-use:" + dto.getUserCouponId();
            couponLockValue = UUID.randomUUID().toString();

            boolean couponLockAcquired = lockProvider.tryLock(
                    couponLockKey, couponLockValue, COUPON_LOCK_TTL_SECONDS);
            if (!couponLockAcquired) {
                log.warn("[OrderService] 쿠폰 락 획득 실패 userCouponId={}", dto.getUserCouponId());
                throw new ConflictException(ErrorCode.COUPON_INVALID);
            }

            try {
                userCoupon = validateAndGetCoupon(dto.getUserCouponId(), userId);
            } catch (RuntimeException e) {
                lockProvider.unlock(couponLockKey, couponLockValue);
                throw e;
            }
        }

        try {
            // ── ③ 금액 계산 ──
            int totalAmount = holdInfos.stream()
                    .mapToInt(info -> info.seat.getSection().getPrice())
                    .sum();
            int discountAmount = (userCoupon != null)
                    ? userCoupon.getCoupon().getDiscountAmount() : 0;
            int finalAmount = Math.max(0, totalAmount - discountAmount);

            // ── ④ ORDER 생성 ──
            Order order = Order.builder()
                    .user(user)
                    .userCoupon(userCoupon)
                    .totalAmount(totalAmount)
                    .discountAmount(discountAmount)
                    .finalAmount(finalAmount)
                    .build();
            orderRepository.save(order);

            // ── ⑤ BOOKING 생성 (각 좌석별) ──
            List<Booking> bookings = new ArrayList<>();
            for (HoldInfo info : holdInfos) {
                Booking booking = Booking.builder()
                        .order(order)
                        .user(user)
                        .seat(info.seat)
                        .event(info.seat.getSection().getEvent())
                        .originalPrice(info.seat.getSection().getPrice())
                        .build();
                bookings.add(bookingRepository.save(booking));
            }

            // ── ⑥ Hold TTL 연장 (+5분, L-02 대응) ──
            // 주문 생성~PG 웹훅 사이의 타이밍 갭 방지
            extendHoldTtl(dto.getHoldTokens(), holdInfos);

            // ── ⑦ Mock PG 결제 요청 (로그로 시뮬레이션) ──
            log.info("[OrderService] Mock PG 결제 요청 orderId={}, amount={}", order.getOrderId(), finalAmount);

            // 응답 DTO 조립
            List<OrderResponseDto.BookingItemDto> items = new ArrayList<>();
            for (int i = 0; i < bookings.size(); i++) {
                Booking b = bookings.get(i);
                HoldInfo info = holdInfos.get(i);
                String seatInfo = b.getSeat().getSection().getSectionName()
                        + " " + b.getSeat().getRowName()
                        + " " + b.getSeat().getColNum() + "번";
                items.add(new OrderResponseDto.BookingItemDto(
                        info.seat.getSeatId(), seatInfo, b.getOriginalPrice()));
            }

            log.info("[OrderService] 주문 생성 성공 orderId={}, userId={}", order.getOrderId(), userId);
            return new OrderResponseDto(order, items);

        } finally {
            // 쿠폰 락은 트랜잭션 COMMIT 후 해제 (WebhookService에서 실제 USE 처리)
            if (couponLockKey != null && couponLockValue != null) {
                lockProvider.unlock(couponLockKey, couponLockValue);
            }
        }
    }

    /**
     * holdToken 역조회 및 소유자 검증
     * Redis holdToken:{uuid} → "{eventId}:{seatId}:{userId}" 파싱
     * TTL 만료 시 409 HOLD_EXPIRED
     */
    private List<HoldInfo> resolveHoldTokens(List<String> holdTokens, Long userId) {
        List<HoldInfo> results = new ArrayList<>();

        for (String holdToken : holdTokens) {
            // holdToken 역조회
            String tokenKey = "holdToken:" + holdToken;
            String tokenValue = redisTemplate.opsForValue().get(tokenKey);

            if (tokenValue == null) {
                log.warn("[OrderService] holdToken 만료 holdToken={}", holdToken);
                throw new HoldExpiredException();
            }

            // "{eventId}:{seatId}:{userId}" 파싱
            String[] parts = tokenValue.split(":");
            if (parts.length != 3) {
                throw new HoldExpiredException();
            }

            Long eventId = Long.parseLong(parts[0]);
            Long seatId  = Long.parseLong(parts[1]);
            Long holdOwnerUserId = Long.parseLong(parts[2]);

            // 소유자 재검증
            if (!holdOwnerUserId.equals(userId)) {
                throw new ForbiddenException(ErrorCode.HOLD_NOT_OWNED);
            }

            // hold 키 소유자 재검증
            String holdKey = "hold:" + eventId + ":" + seatId;
            String holdOwner = redisTemplate.opsForValue().get(holdKey);
            if (holdOwner == null || !holdOwner.equals(String.valueOf(userId))) {
                throw new HoldExpiredException();
            }

            // 좌석 조회
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new NotFoundException(ErrorCode.SEAT_NOT_FOUND));

            results.add(new HoldInfo(holdToken, eventId, seatId, seat));
        }

        return results;
    }

    /**
     * 쿠폰 유효성 검증 (쿠폰 락 내부에서 호출)
     * ISSUED 상태 + 만료일 이내 + 본인 소유 확인
     */
    private UserCoupon validateAndGetCoupon(Long userCouponId, Long userId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));

        // 본인 소유 확인
        if (!userCoupon.getUser().getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.COUPON_NOT_OWNED);
        }
        // ISSUED 상태인지 확인
        if (!UserCouponStatus.ISSUED.equals(userCoupon.getStatus())) {
            throw new InvalidRequestException(ErrorCode.COUPON_INVALID);
        }
        // 만료일 확인 (COUPON_INVALID로 통일 — COUPON_EXPIRED 미정의)
        if (userCoupon.getCoupon().getExpiredAt().isBefore(java.time.LocalDateTime.now())) {
            throw new InvalidRequestException(ErrorCode.COUPON_INVALID);
        }

        return userCoupon;
    }

    /**
     * Hold TTL 연장 (L-02 대응)
     * 주문 생성 성공 직후 호출 — 웹훅 도착 전 TTL 만료 방지
     */
    private void extendHoldTtl(List<String> holdTokens, List<HoldInfo> holdInfos) {
        for (int i = 0; i < holdTokens.size(); i++) {
            String holdToken = holdTokens.get(i);
            HoldInfo info = holdInfos.get(i);

            String holdKey = "hold:" + info.eventId + ":" + info.seatId;
            String tokenKey = "holdToken:" + holdToken;

            redisTemplate.expire(holdKey, Duration.ofSeconds(HOLD_TTL_SECONDS));
            redisTemplate.expire(tokenKey, Duration.ofSeconds(HOLD_TTL_SECONDS));
        }
        log.debug("[OrderService] Hold TTL 연장 완료 holdTokens={}", holdTokens);
    }

    /** holdToken 역조회 결과를 담는 내부 레코드 */
    private record HoldInfo(String holdToken, Long eventId, Long seatId, Seat seat) {}
}
