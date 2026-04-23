package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.WebhookPaymentStatus;
import com.example.ticket_javara.domain.booking.dto.request.WebhookRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.WebhookResponseDto;
import com.example.ticket_javara.domain.booking.entity.Booking;
import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.entity.OrderStatus;
import com.example.ticket_javara.domain.booking.repository.BookingRepository;
import com.example.ticket_javara.domain.booking.repository.OrderRepository;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.entity.Seat;
import com.example.ticket_javara.domain.event.entity.Section;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.global.exception.ConflictException;
import com.example.ticket_javara.global.exception.HoldExpiredException;
import com.example.ticket_javara.global.exception.InvalidRequestException;
import com.example.ticket_javara.global.lock.DistributedLockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @InjectMocks
    private WebhookService webhookService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private DistributedLockProvider lockProvider;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingConfirmService bookingConfirmService;

    @Test
    @DisplayName("웹훅 결제 성공 처리 - 쿠폰 없음")
    void processWebhook_Success_NoCoupon() {
        Long orderId = 1L;
        Long userId = 100L;
        Integer paymentAmount = 150000;
        
        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "orderId", orderId);
        ReflectionTestUtils.setField(requestDto, "paymentKey", "payment-key-1");
        ReflectionTestUtils.setField(requestDto, "paidAmount", paymentAmount);
        ReflectionTestUtils.setField(requestDto, "paymentStatus", WebhookPaymentStatus.SUCCESS);

        User user = mock(User.class);
        given(user.getUserId()).willReturn(userId);

        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn(orderId);
        given(order.getUser()).willReturn(user);
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        given(order.getFinalAmount()).willReturn(paymentAmount);
        
        Event event = mock(Event.class);
        given(event.getEventId()).willReturn(10L);

        Seat seat = mock(Seat.class);
        given(seat.getSeatId()).willReturn(5L);

        Booking booking = mock(Booking.class);
        given(booking.getEvent()).willReturn(event);
        given(booking.getSeat()).willReturn(seat);

        List<Booking> bookings = List.of(booking);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(bookingRepository.findByOrderOrderId(orderId)).willReturn(bookings);
        
        given(redisTemplate.hasKey("hold:10:5")).willReturn(true);
        
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.decrement("user-hold-count:" + userId)).willReturn(0L);

        List<WebhookResponseDto.TicketDto> expectedTickets = List.of(new WebhookResponseDto.TicketDto(1L, "TICKET-1", "A VIP 1번"));
        given(bookingConfirmService.confirmOrder(order, bookings, requestDto)).willReturn(expectedTickets);

        WebhookResponseDto response = webhookService.processWebhook(requestDto);

        assertThat(response.getMessage()).isEqualTo("주문이 확정되었습니다.");
        assertThat(response.getBookings()).hasSize(1);
        
        verify(redisTemplate).delete("hold:10:5");
        verify(redisTemplate).delete("user-hold-count:" + userId);
    }

    @Test
    @DisplayName("웹훅 결제 성공 처리 - 쿠폰 포함/락 획득")
    void processWebhook_Success_WithCoupon() {
        Long orderId = 1L;
        Long userId = 100L;
        Long userCouponId = 99L;
        Integer paymentAmount = 100000;
        
        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "orderId", orderId);
        ReflectionTestUtils.setField(requestDto, "paymentKey", "payment-key-1");
        ReflectionTestUtils.setField(requestDto, "paidAmount", paymentAmount);
        ReflectionTestUtils.setField(requestDto, "paymentStatus", WebhookPaymentStatus.SUCCESS);

        User user = mock(User.class);
        given(user.getUserId()).willReturn(userId);

        UserCoupon userCoupon = mock(UserCoupon.class);
        given(userCoupon.getUserCouponId()).willReturn(userCouponId);

        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn(orderId);
        given(order.getUser()).willReturn(user);
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        given(order.getUserCoupon()).willReturn(userCoupon);
        given(order.getFinalAmount()).willReturn(paymentAmount);
        
        Event event = mock(Event.class);
        given(event.getEventId()).willReturn(10L);

        Seat seat = mock(Seat.class);
        given(seat.getSeatId()).willReturn(5L);

        Booking booking = mock(Booking.class);
        given(booking.getEvent()).willReturn(event);
        given(booking.getSeat()).willReturn(seat);

        List<Booking> bookings = List.of(booking);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(bookingRepository.findByOrderOrderId(orderId)).willReturn(bookings);
        given(redisTemplate.hasKey("hold:10:5")).willReturn(true);
        
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.decrement("user-hold-count:" + userId)).willReturn(0L);

        given(lockProvider.tryLock(eq("lock:user-coupon-use:" + userCouponId), anyString(), anyLong())).willReturn(true);
        given(bookingConfirmService.confirmOrder(order, bookings, requestDto)).willReturn(List.of());

        webhookService.processWebhook(requestDto);

        verify(lockProvider).unlock(eq("lock:user-coupon-use:" + userCouponId), anyString());
        verify(bookingConfirmService).confirmOrder(order, bookings, requestDto);
    }

    @Test
    @DisplayName("결제 금액 불일치로 실패")
    void processWebhook_Fail_PaymentAmountMismatch() {
        Long orderId = 1L;
        Integer orderAmount = 150000;
        Integer webhookAmount = 10000; 

        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "orderId", orderId);
        ReflectionTestUtils.setField(requestDto, "paidAmount", webhookAmount);
        ReflectionTestUtils.setField(requestDto, "paymentStatus", WebhookPaymentStatus.SUCCESS);

        Order order = mock(Order.class);
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        given(order.getFinalAmount()).willReturn(orderAmount);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThrows(InvalidRequestException.class, () -> webhookService.processWebhook(requestDto));
        
        verify(bookingConfirmService).failOrder(orderId);
        verify(bookingConfirmService, never()).confirmOrder(any(), any(), any());
    }

    @Test
    @DisplayName("Hold TTL 만료로 실패")
    void processWebhook_Fail_HoldTtlExpired() {
        Long orderId = 1L;
        Integer paymentAmount = 150000;
        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "orderId", orderId);
        ReflectionTestUtils.setField(requestDto, "paidAmount", paymentAmount);
        ReflectionTestUtils.setField(requestDto, "paymentStatus", WebhookPaymentStatus.SUCCESS);

        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn(orderId);
        given(order.getStatus()).willReturn(OrderStatus.PENDING);
        given(order.getFinalAmount()).willReturn(paymentAmount);
        
        Event event = mock(Event.class);
        given(event.getEventId()).willReturn(10L);

        Seat seat = mock(Seat.class);
        given(seat.getSeatId()).willReturn(5L);

        Booking booking = mock(Booking.class);
        given(booking.getOrder()).willReturn(order);
        given(booking.getEvent()).willReturn(event);
        given(booking.getSeat()).willReturn(seat);

        List<Booking> bookings = List.of(booking);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(bookingRepository.findByOrderOrderId(orderId)).willReturn(bookings);
        
        given(redisTemplate.hasKey("hold:10:5")).willReturn(false);

        assertThrows(HoldExpiredException.class, () -> webhookService.processWebhook(requestDto));
        verify(bookingConfirmService).failOrder(orderId);
    }

    @Test
    @DisplayName("이미 확정된 주문 - 멱등성 응답")
    void processWebhook_Idempotent_AlreadyConfirmed() {
        Long orderId = 1L;
        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "orderId", orderId);
        ReflectionTestUtils.setField(requestDto, "paymentStatus", WebhookPaymentStatus.SUCCESS);

        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn(orderId);
        given(order.getStatus()).willReturn(OrderStatus.CONFIRMED);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        WebhookResponseDto response = webhookService.processWebhook(requestDto);

        assertThat(response.getMessage()).isEqualTo("주문이 확정되었습니다.");
        verify(bookingConfirmService, never()).confirmOrder(any(), any(), any());
        verify(bookingConfirmService, never()).failOrder(any());
    }

    @Test
    @DisplayName("결제 상태가 FAIL 인 웹훅 수신")
    void processWebhook_FailStatus() {
        Long orderId = 1L;
        WebhookRequestDto requestDto = new WebhookRequestDto();
        ReflectionTestUtils.setField(requestDto, "orderId", orderId);
        ReflectionTestUtils.setField(requestDto, "paymentStatus", WebhookPaymentStatus.FAIL);

        WebhookResponseDto response = webhookService.processWebhook(requestDto);

        assertThat(response.getMessage()).isEqualTo("결제 실패로 주문이 취소되었습니다.");
        verify(bookingConfirmService).failOrder(orderId);
        verify(bookingConfirmService, never()).confirmOrder(any(), any(), any());
    }
}
