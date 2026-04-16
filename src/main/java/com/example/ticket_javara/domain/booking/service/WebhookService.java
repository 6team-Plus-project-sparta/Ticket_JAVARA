package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.WebhookPaymentStatus;
import com.example.ticket_javara.domain.booking.dto.request.WebhookRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.WebhookResponseDto;
import com.example.ticket_javara.domain.booking.entity.*;
import com.example.ticket_javara.domain.booking.repository.*;
import com.example.ticket_javara.global.exception.*;
import com.example.ticket_javara.global.lock.DistributedLockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PG 웹훅 수신 처리 서비스 (FN-BK-02)
 *
 * 멱등성 보장: orderId 기준, 이미 CONFIRMED/FAILED인 주문은 재처리하지 않음
 *
 * [트랜잭션 분리 구조]
 * 이 클래스는 오케스트레이션만 담당 (락 획득/해제, Redis 정리, 멱등성 확인)
 * DB 트랜잭션 처리는 BookingConfirmService에 위임
 * → 같은 클래스 내부 호출 시 @Transactional이 무시되는 Spring AOP 한계 해결
 *
 * 성공 시 처리 순서:
 *   1. ORDER 조회 및 PENDING 확인 (멱등성)
 *   2. 결제 금액 2차 검증 (보안)
 *   3. Hold TTL 재검증 (Redis)
 *   4. [쿠폰 적용 시] 쿠폰 사용 락 획득
 *   5. BookingConfirmService.confirmOrder() 호출 (@Transactional — DB 처리)
 *   6. Redis Hold 관련 키 삭제 + user-hold-count DECR
 *   7. [쿠폰 락] finally 해제
 *
 * 실패 시 처리:
 *   BookingConfirmService.failOrder() 호출 → ORDER/BOOKING → FAILED (Hold 유지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final StringRedisTemplate redisTemplate;
    private final DistributedLockProvider lockProvider;

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;

    // ⭐ 트랜잭션 분리: DB 처리는 별도 Bean에 위임 (프록시를 통한 @Transactional 보장)
    private final BookingConfirmService bookingConfirmService;

    private static final long COUPON_LOCK_TTL_SECONDS = 3L;

    /**
     * 웹훅 수신 처리 진입점
     * SUCCESS → 예매 확정, FAIL → 예매 실패
     */
    public WebhookResponseDto processWebhook(WebhookRequestDto dto) {
        if (WebhookPaymentStatus.SUCCESS.equals(dto.getPaymentStatus())) {
            return processSuccess(dto);
        } else {
            return processFail(dto);
        }
    }

    /**
     * 결제 성공 처리
     */
    private WebhookResponseDto processSuccess(WebhookRequestDto dto) {
        String couponLockKey = null;
        String couponLockValue = null;

        // ── 1. ORDER 조회 및 멱등성 확인 ──
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        // 이미 확정된 주문 → 멱등성 보장: 200 즉시 반환
        if (OrderStatus.CONFIRMED.equals(order.getStatus())) {
            log.info("[WebhookService] 이미 확정된 주문 orderId={}", dto.getOrderId());
            return buildSuccessResponse(order, bookingRepository.findByOrderOrderId(order.getOrderId()));
        }
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            log.warn("[WebhookService] PENDING 아닌 주문에 웹훅 수신 orderId={}, status={}",
                    dto.getOrderId(), order.getStatus());
            return new WebhookResponseDto("처리할 수 없는 주문 상태입니다.", dto.getOrderId(), null);
        }

        // ── 2. ⭐ 결제 금액 2차 검증 (Critical 보안 포인트) ──
        if (!order.getFinalAmount().equals(dto.getPaidAmount())) {
            log.error("[WebhookService] 결제 금액 불일치 orderId={}, expected={}, actual={}",
                    dto.getOrderId(), order.getFinalAmount(), dto.getPaidAmount());
            bookingConfirmService.failOrder(dto.getOrderId());
            throw new InvalidRequestException(ErrorCode.VALIDATION_FAILED);
        }

        List<Booking> bookings = bookingRepository.findByOrderOrderId(order.getOrderId());

        // ── 3. Hold TTL 재검증 ──
        validateHoldTtl(bookings);

        // ── 4. [쿠폰 적용 시] 쿠폰 사용 락 획득 ──
        if (order.getUserCoupon() != null) {
            couponLockKey = "lock:user-coupon-use:" + order.getUserCoupon().getUserCouponId();
            couponLockValue = UUID.randomUUID().toString();
            boolean acquired = lockProvider.tryLock(couponLockKey, couponLockValue, COUPON_LOCK_TTL_SECONDS);
            if (!acquired) {
                log.warn("[WebhookService] 쿠폰 락 획득 실패 userCouponId={}",
                        order.getUserCoupon().getUserCouponId());
                throw new ConflictException(ErrorCode.COUPON_INVALID);
            }
        }

        try {
            // ── 5. ⭐ DB 트랜잭션 처리 (별도 Bean 호출 → @Transactional 정상 작동) ──
            List<WebhookResponseDto.TicketDto> tickets =
                    bookingConfirmService.confirmOrder(order, bookings, dto);

            // ── 6. Redis Hold 키 삭제 (트랜잭션 COMMIT 이후) ──
            cleanupHoldKeys(bookings, order.getUser().getUserId());

            log.info("[WebhookService] 예매 확정 성공 orderId={}", dto.getOrderId());
            return new WebhookResponseDto("주문이 확정되었습니다.", dto.getOrderId(), tickets);

        } finally {
            // ── 7. 쿠폰 락 해제 (COMMIT 이후 보장) ──
            if (couponLockKey != null && couponLockValue != null) {
                lockProvider.unlock(couponLockKey, couponLockValue);
            }
        }
    }

    /**
     * 결제 실패 처리
     * ORDER/BOOKING → FAILED, Hold는 유지 (사용자 재시도 가능)
     */
    private WebhookResponseDto processFail(WebhookRequestDto dto) {
        bookingConfirmService.failOrder(dto.getOrderId());
        log.info("[WebhookService] 결제 실패 처리 완료 orderId={}", dto.getOrderId());
        return new WebhookResponseDto("결제 실패로 주문이 취소되었습니다.", dto.getOrderId(), null);
    }

    /**
     * Hold TTL 재검증
     * 하나라도 만료된 경우 → 주문 실패 처리 + HoldExpiredException
     */
    private void validateHoldTtl(List<Booking> bookings) {
        for (Booking booking : bookings) {
            String holdKey = "hold:" + booking.getEvent().getEventId()
                    + ":" + booking.getSeat().getSeatId();
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(holdKey))) {
                log.warn("[WebhookService] Hold TTL 만료 holdKey={}", holdKey);
                bookingConfirmService.failOrder(booking.getOrder().getOrderId());
                throw new HoldExpiredException();
            }
        }
    }

    /**
     * Hold 관련 Redis 키 삭제 (예매 확정 후 호출)
     * - DEL hold:{eventId}:{seatId}     (BOOKING 수만큼)
     * - holdToken:{uuid} 은 TTL 만료로 자연 정리 (UUID 미보관)
     * - DECR user-hold-count:{userId}   (확정 좌석 수만큼)
     */
    private void cleanupHoldKeys(List<Booking> bookings, Long userId) {
        for (Booking booking : bookings) {
            String holdKey = "hold:" + booking.getEvent().getEventId()
                    + ":" + booking.getSeat().getSeatId();
            redisTemplate.delete(holdKey);
        }

        // user-hold-count DECR (확정된 좌석 수만큼)
        String holdCountKey = "user-hold-count:" + userId;
        for (int i = 0; i < bookings.size(); i++) {
            Long count = redisTemplate.opsForValue().decrement(holdCountKey);
            if (count != null && count <= 0) {
                redisTemplate.delete(holdCountKey);
                break;
            }
        }

        log.debug("[WebhookService] Hold 키 삭제 완료 userId={}, seats={}", userId, bookings.size());
    }

    /**
     * 이미 확정된 주문의 성공 응답 구성 (멱등성 처리용)
     */
    private WebhookResponseDto buildSuccessResponse(Order order, List<Booking> bookings) {
        List<WebhookResponseDto.TicketDto> tickets = new ArrayList<>();
        for (Booking b : bookings) {
            if (b.getTicketCode() != null) {
                String seatInfo = b.getSeat().getSection().getSectionName()
                        + " " + b.getSeat().getRowName()
                        + " " + b.getSeat().getColNum() + "번";
                tickets.add(new WebhookResponseDto.TicketDto(
                        b.getBookingId(), b.getTicketCode(), seatInfo));
            }
        }
        return new WebhookResponseDto("주문이 확정되었습니다.", order.getOrderId(), tickets);
    }
}