package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.WebhookRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.WebhookResponseDto;
import com.example.ticket_javara.domain.booking.entity.*;
import com.example.ticket_javara.domain.booking.repository.*;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.global.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 예매 확정 전용 서비스
 *
 * [트랜잭션 설계]
 * - REQUIRES_NEW: 항상 새 트랜잭션에서 실행 (TossPaymentService에 외부 트랜잭션 없음)
 * - findByIdWithLock: 동시 요청 직렬화 — 첫 번째가 CONFIRMED로 커밋하면 두 번째는 멱등 처리
 * - entityManager.persist(): @MapsId 엔티티의 INSERT 강제 (save()는 merge() 호출로 충돌)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingConfirmService {

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final ActiveBookingRepository activeBookingRepository;
    private final PaymentRepository paymentRepository;
    private final UserCouponRepository userCouponRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<WebhookResponseDto.TicketDto> confirmOrder(
            Order order, List<Booking> bookings, WebhookRequestDto dto) {

        // 비관적 락으로 조회 — 동시 요청 직렬화
        Order freshOrder = orderRepository.findByIdWithLock(order.getOrderId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        // 이미 확정된 경우 멱등 처리 (두 번째 동시 요청)
        if (OrderStatus.CONFIRMED.equals(freshOrder.getStatus())) {
            log.info("[BookingConfirmService] 이미 확정된 주문, 멱등 처리 orderId={}", freshOrder.getOrderId());
            return List.of();
        }

        List<Booking> freshBookings = bookingRepository.findByOrderOrderId(freshOrder.getOrderId());

        // a. ORDER → CONFIRMED
        freshOrder.confirm();

        List<WebhookResponseDto.TicketDto> tickets = new ArrayList<>();

        for (Booking booking : freshBookings) {
            // b. ticket_code 생성 및 BOOKING → CONFIRMED
            String ticketCode = generateTicketCode(booking);
            booking.confirm(ticketCode);

            // c. ACTIVE_BOOKING INSERT
            // @MapsId로 seatId가 이미 설정되므로 save()는 merge() 시도 → 충돌
            // entityManager.persist()로 무조건 INSERT
            ActiveBooking activeBooking = ActiveBooking.builder()
                    .seat(booking.getSeat())
                    .booking(booking)
                    .build();
            entityManager.persist(activeBooking);

            String seatInfo = booking.getSeat().getSection().getSectionName()
                    + " " + booking.getSeat().getRowName()
                    + " " + booking.getSeat().getColNum() + "번";
            tickets.add(new WebhookResponseDto.TicketDto(
                    booking.getBookingId(), ticketCode, seatInfo));
        }

        // d. [쿠폰 적용 시] USER_COUPON → USED
        if (freshOrder.getUserCoupon() != null) {
            UserCoupon userCoupon = userCouponRepository
                    .findById(freshOrder.getUserCoupon().getUserCouponId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));

            if (!userCoupon.isUsable()) {
                throw new InvalidRequestException(ErrorCode.COUPON_INVALID);
            }
            userCoupon.use();
        }

        // e. PAYMENT 레코드 생성
        Payment payment = Payment.builder()
                .order(freshOrder)
                .paymentKey(dto.getPaymentKey())
                .method("CARD")
                .paidAmount(dto.getPaidAmount())
                .status(PaymentStatus.SUCCESS)
                .build();
        paymentRepository.save(payment);

        log.info("[BookingConfirmService] 예매 확정 완료 orderId={}, tickets={}",
                freshOrder.getOrderId(), tickets.size());
        return tickets;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        // FAILED 또는 CONFIRMED 상태면 건드리지 않음
        if (OrderStatus.FAILED.equals(order.getStatus())) return;
        if (OrderStatus.CONFIRMED.equals(order.getStatus())) return;

        order.fail();
        bookingRepository.findByOrderOrderId(orderId).forEach(Booking::fail);

        log.info("[BookingConfirmService] 주문 실패 처리 완료 orderId={}", orderId);
    }

    private String generateTicketCode(Booking booking) {
        int year = LocalDate.now().getYear();
        String sectionName = booking.getSeat().getSection().getSectionName();
        String sectionAbbr = sectionName
                .replaceAll("[^A-Za-z0-9가-힣]", "")
                .substring(0, Math.min(2, sectionName.length()));
        String colNum = String.format("%03d", booking.getSeat().getColNum());
        String uuidPrefix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "TF-" + year + "-" + sectionAbbr + colNum + "-" + uuidPrefix;
    }
}