package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.response.OrderDetailResponseDto;
import com.example.ticket_javara.domain.booking.entity.Booking;
import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.repository.BookingRepository;
import com.example.ticket_javara.domain.booking.repository.OrderRepository;
import com.example.ticket_javara.domain.booking.repository.PaymentRepository;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import com.example.ticket_javara.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 상세 조회 서비스 (FN-BK-06)
 *
 * 처리 순서:
 *   1. orderId로 ORDER 조회 (없으면 404)
 *   2. ORDER.userId == JWT userId 확인 (불일치 시 403)
 *   3. BOOKING 목록 조회 (Seat, Section JOIN FETCH — N+1 방지)
 *   4. 서비스에서 seatInfo 문자열 조립 후 BookingItemDto 생성
 *      (DTO 생성자에서 엔티티 탐색하지 않음 — LazyInitializationException 방지)
 *   5. PAYMENT 조회 (PENDING이면 null)
 *   6. 쿠폰 사용 정보 조립 (미사용이면 null)
 *   7. 응답 DTO 반환
 *
 * 폴링 용도:
 *   주문 생성(PENDING) 후 결제 결과 확인(CONFIRMED/FAILED) 폴링 엔드포인트로 활용 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDetailService {

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 주문 상세 조회 (FN-BK-06)
     * readOnly=true: 읽기 전용 트랜잭션 (Dirty Checking 생략 → 성능 향상)
     *
     * @param orderId 조회할 주문 ID
     * @param userId  JWT에서 추출한 현재 사용자 ID
     */
    @Transactional(readOnly = true)
    public OrderDetailResponseDto getOrderDetail(Long orderId, Long userId) {

        // ── 1. ORDER 조회 ──
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        // ── 2. 본인 주문 확인 ──
        if (!order.getUser().getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.ORDER_NOT_OWNED);
        }

        // ── 3. BOOKING 목록 조회 (Seat, Section JOIN FETCH — N+1 방지) ──
        // BookingRepository.findByOrderOrderId(): Seat, Section, Event JOIN FETCH 포함
        List<Booking> bookings = bookingRepository.findByOrderOrderId(orderId);

        // ── 4. ⭐ 서비스에서 seatInfo 조립 후 DTO 생성 ──
        // DTO 생성자에서 getSeat().getSection()... 탐색하지 않음
        // → LazyInitializationException 방지 (트랜잭션 범위 밖 프록시 초기화 실패 방지)
        List<OrderDetailResponseDto.BookingItemDto> bookingItems = bookings.stream()
                .map(booking -> {
                    String seatInfo = booking.getSeat().getSection().getSectionName()
                            + " " + booking.getSeat().getRowName()
                            + " " + booking.getSeat().getColNum() + "번";
                    return new OrderDetailResponseDto.BookingItemDto(
                            booking.getBookingId(),
                            seatInfo,
                            booking.getOriginalPrice(),
                            booking.getTicketCode(),
                            booking.getStatus()
                    );
                })
                .toList();

        // ── 5. PAYMENT 조회 (PENDING 상태면 결제 기록 없음 → null) ──
        OrderDetailResponseDto.PaymentDto paymentDto = paymentRepository
                .findByOrderOrderId(orderId)
                .map(OrderDetailResponseDto.PaymentDto::new)
                .orElse(null);

        // ── 6. 쿠폰 사용 정보 조립 (미사용이면 null) ──
        // UserCoupon → Coupon 탐색: ORDER 조회 시 userCoupon이 LAZY이므로
        // 트랜잭션 내에서 접근해야 정상 동작
        OrderDetailResponseDto.CouponUsedDto couponUsedDto = null;
        if (order.getUserCoupon() != null) {
            couponUsedDto = new OrderDetailResponseDto.CouponUsedDto(
                    order.getUserCoupon().getUserCouponId(),
                    order.getUserCoupon().getCoupon().getName(),
                    order.getUserCoupon().getCoupon().getDiscountAmount()
            );
        }

        log.info("[OrderDetailService] 주문 상세 조회 완료 orderId={}, userId={}", orderId, userId);
        return new OrderDetailResponseDto(order, bookingItems, paymentDto, couponUsedDto);
    }
}