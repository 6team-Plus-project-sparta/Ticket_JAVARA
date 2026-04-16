package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.WebhookRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.WebhookResponseDto;
import com.example.ticket_javara.domain.booking.entity.*;
import com.example.ticket_javara.domain.booking.repository.*;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.global.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 예매 확정 전용 서비스 (트랜잭션 담당)
 *
 * [분리 이유]
 * WebhookService.confirmOrder()가 같은 클래스 내부 호출이면
 * Spring AOP 프록시를 우회하여 @Transactional이 작동하지 않는다.
 * → 별도 Bean으로 분리하여 프록시를 통한 트랜잭션 보장
 *
 * 참고: CLAUDE.md §HoldLockFacade 패턴, 11_백엔드_패키지_구조_설계서.md §3-5
 *
 * 처리 내용 (단일 트랜잭션):
 *   a. ORDER → CONFIRMED
 *   b. BOOKING → CONFIRMED + ticket_code 생성
 *   c. ACTIVE_BOOKING INSERT (seat_id PK 중복 → DataIntegrityViolationException → 롤백, 2차 방어선)
 *   d. [쿠폰 적용 시] USER_COUPON → USED (ISSUED 상태 재검증)
 *   e. PAYMENT 레코드 생성
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

    /**
     * 단일 트랜잭션 내 예매 확정 처리
     * WebhookService에서 호출 — 반드시 별도 Bean 주입을 통해 호출해야 @Transactional 적용됨
     *
     * @param order    확정 대상 주문 (PENDING 상태 보장됨)
     * @param bookings 해당 주문의 예매 목록
     * @param dto      웹훅 요청 DTO (paymentKey, paidAmount 포함)
     * @return 발급된 티켓 목록
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

            // c. ACTIVE_BOOKING INSERT (2차 방어선 — seat_id PK 중복 시 즉시 롤백)
            // DataIntegrityViolationException → GlobalExceptionHandler에서 409 처리
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
            UserCoupon userCoupon = userCouponRepository
                    .findById(order.getUserCoupon().getUserCouponId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.COUPON_NOT_FOUND));

            // 재검증: ISSUED 상태여야 함 (쿠폰 락 내부에서 호출되므로 동시 사용 방지됨)
            if (!userCoupon.isUsable()) {
                throw new InvalidRequestException(ErrorCode.COUPON_INVALID);
            }
            userCoupon.use();
        }

        // e. PAYMENT 레코드 생성
        Payment payment = Payment.builder()
                .order(order)
                .paymentKey(dto.getPaymentKey())
                .method("CARD")  // Mock PG — 실제 연동 시 웹훅에서 method 전달
                .paidAmount(dto.getPaidAmount())
                .status(PaymentStatus.SUCCESS)
                .build();
        paymentRepository.save(payment);

        log.info("[BookingConfirmService] 예매 확정 완료 orderId={}, tickets={}",
                order.getOrderId(), tickets.size());
        return tickets;
    }

    /**
     * 주문/예매 실패 처리 (트랜잭션 보장)
     * Hold TTL 만료, 결제 금액 불일치 등 실패 사유 발생 시 호출
     */
    @Transactional
    public void failOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        // 이미 FAILED면 멱등성 보장
        if (OrderStatus.FAILED.equals(order.getStatus())) return;

        order.fail();
        bookingRepository.findByOrderOrderId(orderId).forEach(Booking::fail);

        log.info("[BookingConfirmService] 주문 실패 처리 완료 orderId={}", orderId);
    }

    /**
     * 티켓 코드 생성
     * 형식: "TF-{연도}-{구역약자}{좌석번호3자리}-{UUID앞4자리대문자}"
     * 예시: TF-2026-A015-XYZ1
     */
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