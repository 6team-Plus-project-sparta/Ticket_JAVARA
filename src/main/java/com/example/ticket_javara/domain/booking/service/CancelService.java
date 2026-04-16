package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.entity.*;
import com.example.ticket_javara.domain.booking.repository.*;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.global.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 취소 서비스 (FN-BK-03)
 *
 * 처리 순서 (단일 트랜잭션, SELECT FOR UPDATE 비관적 락):
 *   1. JWT userId == order.userId 확인
 *   2. ORDER.status == CONFIRMED 확인
 *   3. 이벤트 시작 24시간 전인지 확인 (KST 기준)
 *   4. DB 트랜잭션 (비관적 락):
 *      a. BOOKING 목록 비관적 락 조회
 *      b. ORDER 비관적 락 조회
 *      c. ACTIVE_BOOKING 비관적 락 조회
 *      d. ACTIVE_BOOKING DELETE (좌석 AVAILABLE 복귀)
 *      e. BOOKING → CANCELLED
 *      f. ORDER → CANCELLED
 *      g. [쿠폰 적용 시] USER_COUPON → ISSUED 복원 (비관적 락)
 *   5. Mock PG 환불 요청 (로그 시뮬레이션)
 *
 * 비관적 락 사용 이유 (C-03, L-04 대응):
 *   취소 트랜잭션과 웹훅 확정 트랜잭션이 동시에 실행될 때 충돌 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelService {

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final ActiveBookingRepository activeBookingRepository;
    private final PaymentRepository paymentRepository;
    private final UserCouponRepository userCouponRepository;

    /** 취소 가능 기준: 이벤트 시작 24시간 전 */
    private static final long CANCEL_DEADLINE_HOURS = 24L;

    /**
     * 주문 취소 처리 (FN-BK-03)
     *
     * @param orderId 취소할 주문 ID
     * @param userId  JWT에서 추출한 현재 사용자 ID
     */
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {

        // ── 1. 비관적 락으로 ORDER 조회 ──
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        // ── 2. 본인 주문 확인 ──
        if (!order.getUser().getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.CANCEL_NOT_OWNED);
        }

        // ── 3. 취소 가능한 상태 확인 ──
        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            throw new InvalidRequestException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (!OrderStatus.CONFIRMED.equals(order.getStatus())) {
            // PENDING, FAILED 상태는 취소 불가 (CONFIRMED만 가능)
            throw new InvalidRequestException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        // ── 4. BOOKING 비관적 락 조회 ──
        List<Booking> bookings = bookingRepository.findByOrderOrderIdWithLock(orderId);
        if (bookings.isEmpty()) {
            throw new NotFoundException(ErrorCode.BOOKING_NOT_FOUND);
        }

        // ── 5. 취소 가능 기간 확인 (이벤트 시작 24시간 전, KST 기준) ──
        LocalDateTime eventDate = bookings.get(0).getEvent().getEventDate();
        LocalDateTime cancelDeadline = eventDate.minusHours(CANCEL_DEADLINE_HOURS);
        if (LocalDateTime.now().isAfter(cancelDeadline)) {
            log.warn("[CancelService] 취소 기간 초과 orderId={}, eventDate={}", orderId, eventDate);
            throw new InvalidRequestException(ErrorCode.CANCEL_PERIOD_EXPIRED);
        }

        // ── 6. ACTIVE_BOOKING 비관적 락 조회 ──
        List<Long> seatIds = bookings.stream()
                .map(b -> b.getSeat().getSeatId())
                .toList();
        activeBookingRepository.findBySeatIdInWithLock(seatIds);

        // ── 6. ACTIVE_BOOKING DELETE (좌석 AVAILABLE 복귀) ──
        for (Long seatId : seatIds) {
            activeBookingRepository.deleteBySeatId(seatId);
        }

        // ── 7. BOOKING → CANCELLED ──
        bookings.forEach(Booking::cancel);

        // ── 8. ORDER → CANCELLED ──
        order.cancel();

        // ── 9. [쿠폰 적용 시] USER_COUPON → ISSUED 복원 ──
        if (order.getUserCoupon() != null) {
            // SELECT FOR UPDATE — 복원 중 동일 쿠폰 재사용 시도 충돌 방지 (C-03 대응)
            UserCoupon userCoupon = userCouponRepository
                    .findByIdWithLock(order.getUserCoupon().getUserCouponId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));

            // USED 상태일 때만 복원 (이미 복원됐으면 무시)
            if (!userCoupon.isUsable()) {
                userCoupon.restore();
                log.info("[CancelService] 쿠폰 복원 userCouponId={}", userCoupon.getUserCouponId());
            }
        }

        // ── 10. PAYMENT → REFUNDED ──
        paymentRepository.findByOrderOrderId(orderId)
                .ifPresent(Payment::refund);

        // ── 11. Mock PG 환불 요청 (로그 시뮬레이션) ──
        log.info("[CancelService] Mock PG 환불 요청 orderId={}, userId={}", orderId, userId);
        log.info("[CancelService] 주문 취소 완료 orderId={}", orderId);
    }
}