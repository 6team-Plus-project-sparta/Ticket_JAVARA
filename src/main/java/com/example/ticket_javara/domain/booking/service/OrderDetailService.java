package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.response.OrderDetailResponseDto;
import com.example.ticket_javara.domain.booking.entity.Booking;
import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.entity.Payment;
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
 *   4. PAYMENT 정보 조회 (PENDING이면 null)
 *   5. 쿠폰 사용 정보 조회 (미사용이면 null)
 *   6. 응답 DTO 조립 후 반환
 *
 * 폴링 용도:
 *   주문 생성(PENDING) 후 결제 결과 확인(CONFIRMED/FAILED) 폴링 엔드포인트로 활용 가능
 *   클라이언트가 주기적으로 호출하여 상태 변경 여부 확인
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
     * readOnly=true: 읽기 전용 트랜잭션으로 DB 부하 감소
     *
     * @param orderId 조회할 주문 ID
     * @param userId  JWT에서 추출한 현재 사용자 ID
     * @return 주문 상세 정보 (예매 목록, 결제 정보, 쿠폰 정보 포함)
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
        List<Booking> bookings = bookingRepository.findByOrderOrderId(orderId);
        List<OrderDetailResponseDto.BookingItemDto> bookingItems = bookings.stream()
                .map(OrderDetailResponseDto.BookingItemDto::new)
                .toList();

        // ── 4. PAYMENT 조회 (PENDING 상태면 결제 기록 없음 → null) ──
        OrderDetailResponseDto.PaymentDto paymentDto = paymentRepository
                .findByOrderOrderId(orderId)
                .map(OrderDetailResponseDto.PaymentDto::new)
                .orElse(null);

        // ── 5. 쿠폰 사용 정보 조회 (미사용이면 null) ──
        OrderDetailResponseDto.CouponUsedDto couponUsedDto = null;
        if (order.getUserCoupon() != null) {
            couponUsedDto = new OrderDetailResponseDto.CouponUsedDto(order.getUserCoupon());
        }

        log.info("[OrderDetailService] 주문 상세 조회 orderId={}, userId={}", orderId, userId);
        return new OrderDetailResponseDto(order, bookingItems, paymentDto, couponUsedDto);
    }
}