package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.OrderCreateRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.OrderResponseDto;
import com.example.ticket_javara.domain.booking.entity.Booking;
import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.repository.BookingRepository;
import com.example.ticket_javara.domain.booking.repository.OrderRepository;
import com.example.ticket_javara.domain.coupon.entity.Coupon;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.entity.UserCouponStatus;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.entity.Seat;
import com.example.ticket_javara.domain.event.entity.Section;
import com.example.ticket_javara.domain.event.repository.SeatRepository;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.ConflictException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.HoldExpiredException;
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

import java.time.Duration;
import java.time.LocalDateTime;
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
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

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
    private SeatRepository seatRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Test
    @DisplayName("주문 생성 성공 - 쿠폰 미적용")
    void createOrder_Success_NoCoupon() {
        Long userId = 1L;
        String holdToken = "token123";
        OrderCreateRequestDto requestDto = new OrderCreateRequestDto();
        ReflectionTestUtils.setField(requestDto, "holdTokens", List.of(holdToken));

        User user = mock(User.class);
        given(user.getUserId()).willReturn(userId);

        Event event = mock(Event.class);
        Section section = mock(Section.class);
        given(section.getPrice()).willReturn(150000);
        given(section.getEvent()).willReturn(event);
        given(section.getSectionName()).willReturn("VIP");

        Seat seat = mock(Seat.class);
        given(seat.getSeatId()).willReturn(100L);
        given(seat.getSection()).willReturn(section);
        given(seat.getRowName()).willReturn("A");
        given(seat.getColNum()).willReturn(1);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("holdToken:" + holdToken)).willReturn("10:100:1"); 
        given(valueOperations.get("hold:10:100")).willReturn("1"); 
        
        given(seatRepository.findById(100L)).willReturn(Optional.of(seat));

        Order savedOrder = mock(Order.class);
        given(savedOrder.getOrderId()).willReturn(500L);
        given(savedOrder.getTotalAmount()).willReturn(150000);
        given(savedOrder.getDiscountAmount()).willReturn(0);
        given(savedOrder.getFinalAmount()).willReturn(150000);
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        Booking savedBooking = mock(Booking.class);
        given(savedBooking.getSeat()).willReturn(seat);
        given(savedBooking.getOriginalPrice()).willReturn(150000);
        given(bookingRepository.save(any(Booking.class))).willReturn(savedBooking);

        OrderResponseDto response = orderService.createOrder(requestDto, userId);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getSeatId()).isEqualTo(100L);
        assertThat(response.getItems().get(0).getOriginalPrice()).isEqualTo(150000);

        verify(orderRepository).save(any(Order.class));
        verify(bookingRepository).save(any(Booking.class));
        verify(redisTemplate).expire(eq("hold:10:100"), any(Duration.class));
        verify(redisTemplate).expire(eq("holdToken:" + holdToken), any(Duration.class));
    }

    @Test
    @DisplayName("주문 생성 실패 - Hold 토큰 만료/없음")
    void createOrder_Fail_HoldTokenExpired() {
        Long userId = 1L;
        String holdToken = "token123";
        OrderCreateRequestDto requestDto = new OrderCreateRequestDto();
        ReflectionTestUtils.setField(requestDto, "holdTokens", List.of(holdToken));

        User user = mock(User.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("holdToken:" + holdToken)).willReturn(null); 

        assertThrows(HoldExpiredException.class, () -> orderService.createOrder(requestDto, userId));
    }

    @Test
    @DisplayName("주문 생성 실패 - Hold 소유자가 다름")
    void createOrder_Fail_HoldNotOwned() {
        Long userId = 1L;
        String holdToken = "token123";
        OrderCreateRequestDto requestDto = new OrderCreateRequestDto();
        ReflectionTestUtils.setField(requestDto, "holdTokens", List.of(holdToken));

        User user = mock(User.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("holdToken:" + holdToken)).willReturn("10:100:999"); 

        com.example.ticket_javara.global.exception.ForbiddenException exception = assertThrows(com.example.ticket_javara.global.exception.ForbiddenException.class, 
                () -> orderService.createOrder(requestDto, userId));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.HOLD_NOT_OWNED);
    }

    @Test
    @DisplayName("주문 생성 성공 - 쿠폰 정상 적용 및 할인")
    void createOrder_Success_WithCoupon() {
        Long userId = 1L;
        Long couponId = 50L;
        String holdToken = "token123";
        OrderCreateRequestDto requestDto = new OrderCreateRequestDto();
        ReflectionTestUtils.setField(requestDto, "holdTokens", List.of(holdToken));
        ReflectionTestUtils.setField(requestDto, "userCouponId", couponId);

        User user = mock(User.class);
        given(user.getUserId()).willReturn(userId);

        Event event = mock(Event.class);
        Section section = mock(Section.class);
        given(section.getPrice()).willReturn(150000);
        given(section.getEvent()).willReturn(event);
        given(section.getSectionName()).willReturn("VIP");

        Seat seat = mock(Seat.class);
        given(seat.getSeatId()).willReturn(100L);
        given(seat.getSection()).willReturn(section);
        given(seat.getRowName()).willReturn("A");
        given(seat.getColNum()).willReturn(1);

        Coupon coupon = mock(Coupon.class);
        given(coupon.getDiscountAmount()).willReturn(50000);
        given(coupon.getExpiredAt()).willReturn(LocalDateTime.now().plusDays(1));

        UserCoupon userCoupon = mock(UserCoupon.class);
        given(userCoupon.getUser()).willReturn(user);
        given(userCoupon.getStatus()).willReturn(UserCouponStatus.ISSUED);
        given(userCoupon.getCoupon()).willReturn(coupon);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("holdToken:" + holdToken)).willReturn("10:100:1");
        given(valueOperations.get("hold:10:100")).willReturn("1");
        given(seatRepository.findById(100L)).willReturn(Optional.of(seat));

        given(lockProvider.tryLock(eq("lock:user-coupon-use:" + couponId), anyString(), anyLong())).willReturn(true);
        given(userCouponRepository.findById(couponId)).willReturn(Optional.of(userCoupon));
        
        Order savedOrder = mock(Order.class);
        given(savedOrder.getOrderId()).willReturn(501L);
        given(savedOrder.getTotalAmount()).willReturn(150000);
        given(savedOrder.getDiscountAmount()).willReturn(50000);
        given(savedOrder.getFinalAmount()).willReturn(100000);
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);
        
        Booking savedBooking = mock(Booking.class);
        given(savedBooking.getSeat()).willReturn(seat);
        given(savedBooking.getOriginalPrice()).willReturn(150000);
        given(bookingRepository.save(any(Booking.class))).willReturn(savedBooking);

        OrderResponseDto response = orderService.createOrder(requestDto, userId);

        assertThat(response).isNotNull();
        verify(lockProvider).unlock(eq("lock:user-coupon-use:" + couponId), anyString());
    }

    @Test
    @DisplayName("주문 생성 실패 - 쿠폰 락 획득 실패 시 동시성 예외 발생")
    void createOrder_Fail_CouponLockFailed() {
        Long userId = 1L;
        Long couponId = 50L;
        String holdToken = "token123";
        OrderCreateRequestDto requestDto = new OrderCreateRequestDto();
        ReflectionTestUtils.setField(requestDto, "holdTokens", List.of(holdToken));
        ReflectionTestUtils.setField(requestDto, "userCouponId", couponId);

        User user = mock(User.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("holdToken:" + holdToken)).willReturn("10:100:1");
        given(valueOperations.get("hold:10:100")).willReturn("1");
        
        Section section = mock(Section.class);
        Seat seat = mock(Seat.class);
        given(seatRepository.findById(100L)).willReturn(Optional.of(seat));

        given(lockProvider.tryLock(eq("lock:user-coupon-use:" + couponId), anyString(), anyLong())).willReturn(false);

        ConflictException exception = assertThrows(ConflictException.class, 
                () -> orderService.createOrder(requestDto, userId));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_INVALID);
        
        verify(lockProvider, never()).unlock(anyString(), anyString());
    }
}
