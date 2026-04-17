package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.response.OrderDetailResponseDto;
import com.example.ticket_javara.domain.booking.entity.Booking;
import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.repository.BookingRepository;
import com.example.ticket_javara.domain.booking.repository.OrderRepository;
import com.example.ticket_javara.domain.booking.repository.PaymentRepository;
import com.example.ticket_javara.global.exception.ErrorCode;
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
 *   2. 소유자 검증 — order.validateOwner(userId, ErrorCode) 도메인 메서드 사용
 *   3. BOOKING 목록 조회 (Seat, Section JOIN FETCH — N+1 방지)
 *   4. 서비스에서 seatInfo 문자열 조립 후 BookingItemDto 생성
 *   5. PAYMENT 조회 (PENDING이면 null)
 *   6. 쿠폰 사용 정보 조립 (미사용이면 null)
 *   7. 응답 DTO 반환
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

        // ── 2. ⭐ 소유자 검증 (통합 도메인 메서드 — 에러코드 파라미터로 전달) ──
        order.validateOwner(userId, ErrorCode.ORDER_NOT_OWNED);

        // ── 3. BOOKING 목록 조회 (Seat, Section JOIN FETCH — N+1 방지) ──
        List<Booking> bookings = bookingRepository.findByOrderOrderId(orderId);

        // ── 4. ⭐ 서비스에서 seatInfo 조립 후 DTO 생성 ──
        // DTO 생성자에서 getSeat().getSection()... 탐색하지 않음
        // → LazyInitializationException 방지
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