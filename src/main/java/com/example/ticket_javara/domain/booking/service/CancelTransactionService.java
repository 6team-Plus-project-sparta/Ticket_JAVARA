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
 * 주문 취소 트랜잭션 전담 서비스
 *
 * [분리 이유]
 * CancelService.cancelOrder()(트랜잭션 없음)에서 같은 클래스 내부의
 * @Transactional 메서드를 호출하면 Spring AOP 프록시를 우회하여 트랜잭션 미적용
 * → 별도 Bean으로 분리하여 프록시를 통한 트랜잭션 보장 (Self-invocation 문제 해결)
 *
 * 처리 내용 (단일 트랜잭션, SELECT FOR UPDATE 비관적 락):
 *   1. ORDER 비관적 락 조회
 *   2. 소유자 검증 — order.validateOwner(userId, ErrorCode) 도메인 메서드 사용
 *   3. CONFIRMED 상태 확인
 *   4. BOOKING 비관적 락 조회
 *   5. isEmpty() 확인 (get(0) 호출 전 반드시 수행)
 *   6. 이벤트 시작 24시간 전 확인 (KST 기준)
 *   7. ACTIVE_BOOKING 비관적 락 조회
 *   8. ACTIVE_BOOKING 벌크 DELETE (좌석 AVAILABLE 복귀)
 *   9. BOOKING → CANCELLED
 *  10. ORDER → CANCELLED
 *  11. [쿠폰 적용 시] USER_COUPON → ISSUED 복원 (비관적 락)
 *  12. PAYMENT → REFUNDED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelTransactionService {

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final ActiveBookingRepository activeBookingRepository;
    private final PaymentRepository paymentRepository;
    private final UserCouponRepository userCouponRepository;

    /** 취소 가능 기준: 이벤트 시작 24시간 전 */
    private static final long CANCEL_DEADLINE_HOURS = 24L;

    /**
     * 주문 취소 DB 처리 (트랜잭션 보장)
     * CancelService.cancelOrder()에서 호출
     *
     * @param orderId 취소할 주문 ID
     * @param userId  JWT에서 추출한 현재 사용자 ID
     */
    @Transactional
    public void execute(Long orderId, Long userId) {

        // ── 1. 비관적 락으로 ORDER 조회 ──
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        // ── 2. ⭐ 소유자 검증 (통합 도메인 메서드 — 에러코드 파라미터로 전달) ──
        order.validateOwner(userId, ErrorCode.CANCEL_NOT_OWNED);

        // ── 3. 취소 가능한 상태 확인 ──
        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            throw new InvalidRequestException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (!OrderStatus.CONFIRMED.equals(order.getStatus())) {
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
            log.warn("[CancelTransactionService] 취소 기간 초과 orderId={}, eventDate={}",
                    orderId, eventDate);
            throw new InvalidRequestException(ErrorCode.CANCEL_PERIOD_EXPIRED);
        }

        // ── 7. ACTIVE_BOOKING 비관적 락 조회 ──
        List<Long> seatIds = bookings.stream()
                .map(b -> b.getSeat().getSeatId())
                .toList();
        activeBookingRepository.findBySeatIdInWithLock(seatIds);

        // ── 8. ⭐ ACTIVE_BOOKING 벌크 DELETE (N+1 방지) ──
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

            if (!userCoupon.isUsable()) {
                userCoupon.restore();
                log.info("[CancelTransactionService] 쿠폰 복원 완료 userCouponId={}",
                        userCoupon.getUserCouponId());
            }
        }

        // ── 12. PAYMENT → REFUNDED ──
        paymentRepository.findByOrderOrderId(orderId)
                .ifPresent(Payment::refund);

        log.info("[CancelTransactionService] 취소 DB 처리 완료 orderId={}", orderId);
    }
}