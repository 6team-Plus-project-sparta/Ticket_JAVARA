package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.WebhookPaymentStatus;
import com.example.ticket_javara.domain.booking.dto.request.WebhookRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.WebhookResponseDto;
import com.example.ticket_javara.domain.booking.entity.*;
import com.example.ticket_javara.domain.booking.repository.*;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.global.exception.*;
import com.example.ticket_javara.global.lock.DistributedLockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PG 웹훅 수신 처리 서비스 (FN-BK-02)
 *
 * 멱등성 보장: orderId 기준, 이미 CONFIRMED/FAILED인 주문은 재처리하지 않음
 *
 * 성공 시 처리 순서 (단일 트랜잭션):
 *   1. ORDER 조회 및 PENDING 확인 (멱등성)
 *   2. Hold TTL 재검증 (Redis hold 키 존재 확인)
 *   3. [쿠폰 적용 시] 쿠폰 사용 락 획득 + USER_COUPON 상태 재검증
 *   4. DB 트랜잭션 (단일 원자적):
 *      a. ORDER → CONFIRMED
 *      b. BOOKING → CONFIRMED + ticket_code 생성
 *      c. ACTIVE_BOOKING INSERT (seat_id PK 중복 → 롤백, 2차 방어선)
 *      d. [쿠폰] USER_COUPON → USED
 *      e. PAYMENT 레코드 생성
 *   5. Redis Hold 관련 키 삭제 + user-hold-count DECR
 *   6. [쿠폰 락] finally 해제
 *
 * 실패 시 처리:
 *   ORDER → FAILED, BOOKING → FAILED (Hold는 유지 — 재시도 가능)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final StringRedisTemplate redisTemplate;
    private final DistributedLockProvider lockProvider;

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final ActiveBookingRepository activeBookingRepository;
    private final PaymentRepository paymentRepository;
    private final UserCouponRepository userCouponRepository;

    private static final long COUPON_LOCK_TTL_SECONDS = 3L;

    /**
     * 웹훅 수신 처리 진입점
     * paymentStatus == "SUCCESS" → 예매 확정
     * paymentStatus == "FAIL"    → 예매 실패
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
        // 쿠폰 락은 트랜잭션 외부에서 관리
        String couponLockKey = null;
        String couponLockValue = null;

        // ── 1. ORDER 조회 및 멱등성 확인 ──
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        // 이미 처리된 주문 → 멱등성 보장: 200 즉시 반환
        if (OrderStatus.CONFIRMED.equals(order.getStatus())) {
            log.info("[WebhookService] 이미 확정된 주문 orderId={}", dto.getOrderId());
            return buildSuccessResponse(order, bookingRepository.findByOrderOrderId(order.getOrderId()));
        }
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            log.warn("[WebhookService] PENDING 아닌 주문에 웹훅 수신 orderId={}, status={}",
                    dto.getOrderId(), order.getStatus());
            return new WebhookResponseDto("처리할 수 없는 주문 상태입니다.", dto.getOrderId(), null);
        }

        // ── ⭐ 결제 금액 2차 검증 (Critical 보안 포인트) ──
        if (!order.getFinalAmount().equals(dto.getPaidAmount())) {
            log.error("[WebhookService] 결제 금액 불일치. orderId={}, expected={}, actual={}",
                    dto.getOrderId(), order.getFinalAmount(), dto.getPaidAmount());
            failOrder(dto.getOrderId());
            throw new InvalidRequestException(ErrorCode.VALIDATION_FAILED);
        }

        List<Booking> bookings = bookingRepository.findByOrderOrderId(order.getOrderId());

        // ── 2. Hold TTL 재검증 ──
        validateHoldTtl(bookings);

        // ── 3. [쿠폰 적용 시] 쿠폰 락 획득 ──
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
            // ── 4. DB 트랜잭션 (원자적 처리) ──
            List<WebhookResponseDto.TicketDto> tickets = confirmOrder(order, bookings, dto);

            // ── 5. Redis Hold 키 삭제 ──
            cleanupHoldKeys(bookings, order.getUser().getUserId());

            log.info("[WebhookService] 예매 확정 성공 orderId={}", dto.getOrderId());
            return new WebhookResponseDto("주문이 확정되었습니다.", dto.getOrderId(), tickets);

        } finally {
            // ── 6. 쿠폰 락 해제 (COMMIT 이후 보장) ──
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
        failOrder(dto.getOrderId());
        log.info("[WebhookService] 결제 실패 처리 완료 orderId={}", dto.getOrderId());
        return new WebhookResponseDto("결제 실패로 주문이 취소되었습니다.", dto.getOrderId(), null);
    }

    /**
     * Hold TTL 재검증
     * 하나라도 만료된 경우 → 예매 실패 처리 + HoldExpiredException
     */
    private void validateHoldTtl(List<Booking> bookings) {
        for (Booking booking : bookings) {
            String holdKey = "hold:" + booking.getEvent().getEventId()
                    + ":" + booking.getSeat().getSeatId();
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(holdKey))) {
                log.warn("[WebhookService] Hold TTL 만료 holdKey={}", holdKey);
                // 주문 실패 처리
                failOrder(booking.getOrder().getOrderId());
                throw new HoldExpiredException();
            }
        }
    }

    /**
     * 단일 트랜잭션 내 예매 확정 처리
     * a. ORDER → CONFIRMED
     * b. BOOKING → CONFIRMED + ticket_code 생성
     * c. ACTIVE_BOOKING INSERT (PK 중복 → DataIntegrityViolationException → 롤백)
     * d. [쿠폰] USER_COUPON → USED
     * e. PAYMENT 레코드 생성
     */
    @Transactional
    public List<WebhookResponseDto.TicketDto> confirmOrder(
            Order order, List<Booking> bookings, WebhookRequestDto dto) {

        // a. ORDER → CONFIRMED
        order.confirm();

        List<WebhookResponseDto.TicketDto> tickets = new ArrayList<>();

        for (Booking booking : bookings) {
            // b. ticket_code 생성 및 BOOKING → CONFIRMED
            String ticketCode = generateTicketCode(booking);
            booking.confirm(ticketCode);

            // c. ACTIVE_BOOKING INSERT (2차 방어선 — PK 중복 시 즉시 롤백)
            ActiveBooking activeBooking = ActiveBooking.builder()
                    .seat(booking.getSeat())
                    .booking(booking)
                    .build();
            activeBookingRepository.save(activeBooking);

            String seatInfo = booking.getSeat().getSection().getSectionName()
                    + " " + booking.getSeat().getRowName()
                    + " " + booking.getSeat().getColNum() + "번";
            tickets.add(new WebhookResponseDto.TicketDto(
                    booking.getBookingId(), ticketCode, seatInfo));
        }

        // d. [쿠폰 적용 시] USER_COUPON → USED
        if (order.getUserCoupon() != null) {
            UserCoupon userCoupon = userCouponRepository.findById(
                            order.getUserCoupon().getUserCouponId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));
            // 재검증: ISSUED 상태여야 함
            if (!userCoupon.isUsable()) {
                throw new InvalidRequestException(ErrorCode.COUPON_INVALID);
            }
            userCoupon.use();
        }

        // e. PAYMENT 레코드 생성
        Payment payment = Payment.builder()
                .order(order)
                .paymentKey(dto.getPaymentKey())
                .method("CARD")  // Mock PG — 실제는 웹훅에서 method 전달
                .paidAmount(dto.getPaidAmount())
                .status(PaymentStatus.SUCCESS)
                .build();
        paymentRepository.save(payment);

        return tickets;
    }

    /**
     * 주문 실패 처리
     */
    @Transactional
    public void failOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        if (OrderStatus.FAILED.equals(order.getStatus())) return;

        order.fail();
        bookingRepository.findByOrderOrderId(orderId).forEach(Booking::fail);
    }

    /**
     * Hold 관련 Redis 키 삭제 (예매 확정 후)
     * - DEL hold:{eventId}:{seatId}
     * - DEL holdToken:{uuid}  ← holdToken UUID 미보관으로 TTL 만료 정리
     * - DECR user-hold-count:{userId}
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
     * 성공 응답 구성 (이미 확정된 주문 처리 시 사용)
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

    /**
     * 티켓 코드 생성
     * 형식: "TF-{연도}-{구역약자}{좌석번호}-{UUID앞4자리}"
     * 예: TF-2026-A015-XYZ1
     */
    private String generateTicketCode(Booking booking) {
        int year = LocalDate.now().getYear();
        String sectionAbbr = booking.getSeat().getSection().getSectionName()
                .replaceAll("[^A-Za-z0-9가-힣]", "")
                .substring(0, Math.min(2,
                        booking.getSeat().getSection().getSectionName().length()));
        String colNum = String.format("%03d", booking.getSeat().getColNum());
        String uuidPrefix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "TF-" + year + "-" + sectionAbbr + colNum + "-" + uuidPrefix;
    }
}
