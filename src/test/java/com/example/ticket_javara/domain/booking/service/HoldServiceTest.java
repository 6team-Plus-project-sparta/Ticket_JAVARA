package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.response.HoldResponseDto;
import com.example.ticket_javara.domain.booking.repository.ActiveBookingRepository;
import com.example.ticket_javara.global.exception.ConflictException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import com.example.ticket_javara.global.exception.InvalidRequestException;
import com.example.ticket_javara.global.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {

    @InjectMocks
    private HoldService holdService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ActiveBookingRepository activeBookingRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final Long EVENT_ID = 1L;
    private static final Long SEAT_ID = 1L;
    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        // opsForValue()가 호출되면 valueOperations Mock을 반환하도록 설정할 수도 있지만
        // LenientMocking을 위해 BDDMockito보다는 필요할 때만 설정합니다.
    }

    @Test
    @DisplayName("Hold 성공 - 모든 검증 통과")
    void processHold_Success() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("user-hold-count:" + USER_ID)).willReturn("2");
        given(activeBookingRepository.existsBySeatId(SEAT_ID)).willReturn(false);
        given(redisTemplate.hasKey("hold:" + EVENT_ID + ":" + SEAT_ID)).willReturn(false);
        given(valueOperations.increment("user-hold-count:" + USER_ID)).willReturn(3L);

        // when
        HoldResponseDto response = holdService.processHold(EVENT_ID, SEAT_ID, USER_ID);

        // then
        assertThat(response.getSeatId()).isEqualTo(SEAT_ID);
        assertThat(response.getHoldToken()).isNotNull();

        verify(valueOperations).set(eq("hold:" + EVENT_ID + ":" + SEAT_ID), eq(String.valueOf(USER_ID)), any(Duration.class));
        verify(valueOperations).set(eq("holdToken:" + response.getHoldToken()), eq(EVENT_ID + ":" + SEAT_ID + ":" + USER_ID), any(Duration.class));
        verify(valueOperations).increment("user-hold-count:" + USER_ID);
    }

    @Test
    @DisplayName("Hold 실패 - Hold 한도 초과(4석)")
    void processHold_Fail_LimitExceeded() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("user-hold-count:" + USER_ID)).willReturn("4");

        // when & then
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                () -> holdService.processHold(EVENT_ID, SEAT_ID, USER_ID));
        
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.HOLD_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Hold 실패 - 이미 확정된 좌석")
    void processHold_Fail_AlreadyConfirmed() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("user-hold-count:" + USER_ID)).willReturn("1");
        given(activeBookingRepository.existsBySeatId(SEAT_ID)).willReturn(true);

        // when & then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> holdService.processHold(EVENT_ID, SEAT_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SEAT_ALREADY_CONFIRMED);
    }

    @Test
    @DisplayName("Hold 실패 - 이미 누군가 선점한 좌석")
    void processHold_Fail_AlreadyHeld() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("user-hold-count:" + USER_ID)).willReturn("1");
        given(activeBookingRepository.existsBySeatId(SEAT_ID)).willReturn(false);
        given(redisTemplate.hasKey("hold:" + EVENT_ID + ":" + SEAT_ID)).willReturn(true);

        // when & then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> holdService.processHold(EVENT_ID, SEAT_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SEAT_ALREADY_HELD);
    }

    @Test
    @DisplayName("Hold 해제 성공")
    void releaseHold_Success() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("hold:" + EVENT_ID + ":" + SEAT_ID)).willReturn(String.valueOf(USER_ID));
        given(valueOperations.decrement("user-hold-count:" + USER_ID)).willReturn(0L);

        // when
        holdService.releaseHold(EVENT_ID, SEAT_ID, USER_ID);

        // then
        verify(redisTemplate).delete("hold:" + EVENT_ID + ":" + SEAT_ID);
        verify(redisTemplate).delete("user-hold-count:" + USER_ID); // count가 0이 되면 삭제 삭제
    }

    @Test
    @DisplayName("Hold 해제 실패 - Hold 존재 안함")
    void releaseHold_Fail_NotFound() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("hold:" + EVENT_ID + ":" + SEAT_ID)).willReturn(null);

        // when & then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> holdService.releaseHold(EVENT_ID, SEAT_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.HOLD_NOT_FOUND);
    }

    @Test
    @DisplayName("Hold 해제 실패 - 자신의 소유가 아님")
    void releaseHold_Fail_NotOwned() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("hold:" + EVENT_ID + ":" + SEAT_ID)).willReturn("999"); // 다른 사람 userId

        // when & then
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> holdService.releaseHold(EVENT_ID, SEAT_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.HOLD_NOT_OWNED);
    }
}
