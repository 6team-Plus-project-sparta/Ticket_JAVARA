package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.OrderCreateRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.OrderResponseDto;
import com.example.ticket_javara.domain.booking.entity.Booking;
import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.entity.OrderStatus;
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

    @Transactional
    public OrderResponseDto createOrder(OrderCreateRequestDto dto, Long userId) {

        // ── 사용자 조회 ──
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        // ── ① holdToken 역조회 및 검증 ──
        List<HoldInfo> holdInfos = resolveHoldTokens(dto.getHoldTokens(), userId);

        // ── ② [쿠폰 적용 시] 쿠폰 유효성 검증 ──
        UserCoupon userCoupon = null;
        String couponLockKey = null;
        String couponLockValue = null;

        if (dto.getUserCouponId() != null) {
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
            // ── ②-1. 같은 쿠폰 사용한 기존 PENDING 주문 FAILED 처리 ──
            // 결제 실패/미완료로 인해 쿠폰이 PENDING 주문에 묶인 경우 해제
            if (userCoupon != null) {
                List<Order> pendingOrders = orderRepository
                        .findByUserCouponUserCouponIdAndStatus(
                                userCoupon.getUserCouponId(), OrderStatus.PENDING);
                for (Order pendingOrder : pendingOrders) {
                    pendingOrder.fail();
                    bookingRepository.findByOrderOrderId(pendingOrder.getOrderId())
                            .forEach(Booking::fail);
                    log.info("[OrderService] 기존 PENDING 주문 FAILED 처리 orderId={}",
                            pendingOrder.getOrderId());
                }
            }

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
            if (couponLockKey != null && couponLockValue != null) {
                lockProvider.unlock(couponLockKey, couponLockValue);
            }
        }
    }

    private List<HoldInfo> resolveHoldTokens(List<String> holdTokens, Long userId) {
        List<HoldInfo> results = new ArrayList<>();

        for (String holdToken : holdTokens) {
            String tokenKey = "holdToken:" + holdToken;
            String tokenValue = redisTemplate.opsForValue().get(tokenKey);

            if (tokenValue == null) {
                log.warn("[OrderService] holdToken 만료 holdToken={}", holdToken);
                throw new HoldExpiredException();
            }

            String[] parts = tokenValue.split(":");
            if (parts.length != 3) {
                throw new HoldExpiredException();
            }

            Long eventId = Long.parseLong(parts[0]);
            Long seatId  = Long.parseLong(parts[1]);
            Long holdOwnerUserId = Long.parseLong(parts[2]);

            if (!holdOwnerUserId.equals(userId)) {
                throw new ForbiddenException(ErrorCode.HOLD_NOT_OWNED);
            }

            String holdKey = "hold:" + eventId + ":" + seatId;
            String holdOwner = redisTemplate.opsForValue().get(holdKey);
            if (holdOwner == null || !holdOwner.equals(String.valueOf(userId))) {
                throw new HoldExpiredException();
            }

            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new NotFoundException(ErrorCode.SEAT_NOT_FOUND));

            results.add(new HoldInfo(holdToken, eventId, seatId, seat));
        }

        return results;
    }

    private UserCoupon validateAndGetCoupon(Long userCouponId, Long userId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));

        if (!userCoupon.getUser().getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.COUPON_NOT_OWNED);
        }
        if (!UserCouponStatus.ISSUED.equals(userCoupon.getStatus())) {
            throw new InvalidRequestException(ErrorCode.COUPON_INVALID);
        }
        if (userCoupon.getCoupon().getExpiredAt().isBefore(java.time.LocalDateTime.now())) {
            throw new InvalidRequestException(ErrorCode.COUPON_INVALID);
        }

        return userCoupon;
    }

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

    private record HoldInfo(String holdToken, Long eventId, Long seatId, Seat seat) {}
}