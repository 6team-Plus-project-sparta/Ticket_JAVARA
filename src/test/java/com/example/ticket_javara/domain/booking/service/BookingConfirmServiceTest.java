package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.WebhookRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.WebhookResponseDto;
import com.example.ticket_javara.domain.booking.entity.*;
import com.example.ticket_javara.domain.booking.repository.*;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.entity.UserCouponStatus;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.domain.event.entity.Seat;
import com.example.ticket_javara.domain.event.entity.Section;
import com.example.ticket_javara.global.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BookingConfirmServiceTest {

    @InjectMocks
    private BookingConfirmService bookingConfirmService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ActiveBookingRepository activeBookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private EntityManager entityManager;

    @Test
    @DisplayName("주문 확정 성공 - PENDING 주문이 CONFIRMED로 변경됨")
    void confirmOrder_Success() {
        Long orderId = 1L;
        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn(orderId);
        given(order.getStatus()).willReturn(OrderStatus.PENDING);

        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "orderId", orderId);
        ReflectionTestUtils.setField(requestDto, "paymentKey", "payment-key-123");
        ReflectionTestUtils.setField(requestDto, "paidAmount", 150000);

        Section section = mock(Section.class);
        given(section.getSectionName()).willReturn("VIP");

        Seat seat = mock(Seat.class);
        given(seat.getSection()).willReturn(section);
        given(seat.getRowName()).willReturn("A");
        given(seat.getColNum()).willReturn(1);

        Booking booking = mock(Booking.class);
        given(booking.getSeat()).willReturn(seat);

        given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));
        given(bookingRepository.findByOrderOrderId(orderId)).willReturn(List.of(booking));

        List<WebhookResponseDto.TicketDto> tickets = bookingConfirmService.confirmOrder(order, List.of(booking), requestDto);

        assertThat(tickets).hasSize(1);
        verify(order).confirm();
        verify(booking).confirm(anyString());
        verify(entityManager).persist(any(ActiveBooking.class));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("주문 확정 멱등성 - 이미 CONFIRMED 주문은 재처리 안함")
    void confirmOrder_Idempotent_AlreadyConfirmed() {
        Long orderId = 1L;
        Order freshOrder = mock(Order.class);
        given(freshOrder.getOrderId()).willReturn(orderId);
        given(freshOrder.getStatus()).willReturn(OrderStatus.CONFIRMED);

        Order reqOrder = mock(Order.class);
        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "orderId", orderId);
        ReflectionTestUtils.setField(requestDto, "paymentKey", "payment-key-123");
        ReflectionTestUtils.setField(requestDto, "paidAmount", 150000);

        given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(freshOrder));

        List<WebhookResponseDto.TicketDto> tickets = bookingConfirmService.confirmOrder(reqOrder, List.of(), requestDto);

        assertThat(tickets).isEmpty();
        verify(bookingRepository, never()).findByOrderOrderId(orderId);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("주문 확정 실패 - 찾을 수 없는 주문")
    void confirmOrder_Fail_NotFound() {
        Long orderId = 1L;
        Order reqOrder = mock(Order.class);
        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "orderId", orderId);

        given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> bookingConfirmService.confirmOrder(reqOrder, List.of(), requestDto));
    }

    @Test
    @DisplayName("주문 확정 성공 - 쿠폰 적용됨")
    void confirmOrder_Success_WithCoupon() {
        Long orderId = 1L;
        Long userCouponId = 5L;

        UserCoupon reqUserCoupon = mock(UserCoupon.class);
        given(reqUserCoupon.getUserCouponId()).willReturn(userCouponId);

        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn(orderId);
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        given(order.getUserCoupon()).willReturn(reqUserCoupon);

        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "paymentKey", "payment-key-123");
        ReflectionTestUtils.setField(requestDto, "paidAmount", 100000);

        Section section = mock(Section.class);
        given(section.getSectionName()).willReturn("VIP");

        Seat seat = mock(Seat.class);
        given(seat.getSection()).willReturn(section);
        given(seat.getRowName()).willReturn("A");
        given(seat.getColNum()).willReturn(1);

        Booking booking = mock(Booking.class);
        given(booking.getSeat()).willReturn(seat);

        UserCoupon freshUserCoupon = mock(UserCoupon.class);
        given(freshUserCoupon.isUsable()).willReturn(true);

        given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));
        given(bookingRepository.findByOrderOrderId(orderId)).willReturn(List.of(booking));
        given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(freshUserCoupon));

        bookingConfirmService.confirmOrder(order, List.of(booking), requestDto);

        verify(freshUserCoupon).use();
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("주문 실패 처리 성공")
    void failOrder_Success() {
        Long orderId = 1L;
        Order order = mock(Order.class);
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        
        Booking booking = mock(Booking.class);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(bookingRepository.findByOrderOrderId(orderId)).willReturn(List.of(booking));

        bookingConfirmService.failOrder(orderId);

        verify(order).fail();
        verify(booking).fail();
    }
}
