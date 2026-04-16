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
 * 처리 순서:
 *   [트랜잭션 내] cancelOrderInTransaction()
 *     1. ORDER 비관적 락 조회
 *     2. 본인 주문 확인
 *     3. CONFIRMED 상태 확인
 *     4. BOOKING 비관적 락 조회
 *     5. isEmpty() 확인 (get(0) 호출 전 반드시 수행)
 *     6. 이벤트 시작 24시간 전 확인 (KST 기준)
 *     7. ACTIVE_BOOKING 비관적 락 조회
 *     8. ACTIVE_BOOKING 벌크 DELETE (좌석 AVAILABLE 복귀)
 *     9. BOOKING → CANCELLED
 *    10. ORDER → CANCELLED
 *    11. [쿠폰 적용 시] USER_COUPON → ISSUED 복원 (비관적 락)
 *    12. PAYMENT → REFUNDED
 *   [트랜잭션 외부] Mock PG 환불 요청 (로그 시뮬레이션)
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
     * 주문 취소 진입점 (FN-BK-03)
     * DB 처리(트랜잭션)와 Mock PG 환불(트랜잭션 외부)을 분리
     *
     * [Mock PG를 트랜잭션 외부에서 호출하는 이유]
     * @Transactional 안에서 외부 API를 호출하면 DB 커넥션을 붙잡고 응답을 대기함
     * PG사 응답 지연 → DB 커넥션 고갈 → 서비스 전체 장애(장애 전파, Cascading Failure)
     * 실제 연동 시에는 MQ(Message Queue) 또는 별도 비동기 처리 권장
     */
    public void cancelOrder(Long orderId, Long userId) {
        // ① DB 처리 (트랜잭션 내)
        cancelOrderInTransaction(orderId, userId);

        // ② Mock PG 환불 요청 (트랜잭션 외부 — 커넥션 고갈 방지)
        log.info("[CancelService] Mock PG 환불 요청 orderId={}", orderId);
        log.info("[CancelService] 주문 취소 완료 orderId={}, userId={}", orderId, userId);
    }

    /**
     * 취소 DB 처리 전담 (트랜잭션 보장)
     * cancelOrder()에서 호출 — 별도 메서드로 분리하여 Mock PG 호출과 트랜잭션 범위 분리
     */
    @Transactional
    public void cancelOrderInTransaction(Long orderId, Long userId) {

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

        // ── 5. ⭐ isEmpty() 체크 — get(0) 호출 전에 반드시 수행 ──
        if (bookings.isEmpty()) {
            throw new NotFoundException(ErrorCode.BOOKING_NOT_FOUND);
        }

        // ── 6. 취소 가능 기간 확인 (이벤트 시작 24시간 전, KST 기준) ──
        // 동일 ORDER 내 BOOKING은 모두 같은 EVENT에 속함 (FN-BK-01 설계 기준)
        LocalDateTime eventDate = bookings.get(0).getEvent().getEventDate();
        LocalDateTime cancelDeadline = eventDate.minusHours(CANCEL_DEADLINE_HOURS);
        if (LocalDateTime.now().isAfter(cancelDeadline)) {
            log.warn("[CancelService] 취소 기간 초과 orderId={}, eventDate={}", orderId, eventDate);
            throw new InvalidRequestException(ErrorCode.CANCEL_PERIOD_EXPIRED);
        }

        // ── 7. ACTIVE_BOOKING 비관적 락 조회 ──
        List<Long> seatIds = bookings.stream()
                .map(b -> b.getSeat().getSeatId())
                .toList();
        activeBookingRepository.findBySeatIdInWithLock(seatIds);

        // ── 8. ⭐ ACTIVE_BOOKING 벌크 DELETE (N+1 방지) ──
        // 개별 deleteBySeatId() 루프 대신 IN 절 단일 쿼리로 처리
        // DELETE FROM active_booking WHERE seat_id IN (...)
        activeBookingRepository.deleteAllBySeatIdIn(seatIds);

        // ── 9. BOOKING → CANCELLED ──
        bookings.forEach(Booking::cancel);

        // ── 10. ORDER → CANCELLED ──
        order.cancel();

        // ── 11. [쿠폰 적용 시] USER_COUPON → ISSUED 복원 ──
        if (order.getUserCoupon() != null) {
            // SELECT FOR UPDATE — 복원 중 동일 쿠폰 재사용 시도 충돌 방지 (C-03 대응)
            UserCoupon userCoupon = userCouponRepository
                    .findByIdWithLock(order.getUserCoupon().getUserCouponId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));

            // USED 상태일 때만 복원 (이미 복원됐으면 무시)
            if (!userCoupon.isUsable()) {
                userCoupon.restore();
                log.info("[CancelService] 쿠폰 복원 완료 userCouponId={}", userCoupon.getUserCouponId());
            }
        }

        // ── 12. PAYMENT → REFUNDED ──
        paymentRepository.findByOrderOrderId(orderId)
                .ifPresent(Payment::refund);
    }
}