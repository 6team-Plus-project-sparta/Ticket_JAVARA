package com.example.ticket_javara.domain.coupon.service;

import com.example.ticket_javara.domain.coupon.dto.CreateCouponRequest;
import com.example.ticket_javara.domain.coupon.dto.CreateCouponResponse;
import com.example.ticket_javara.domain.coupon.dto.GetCouponResponse;
import com.example.ticket_javara.domain.coupon.dto.IssueCouponResponse;
import com.example.ticket_javara.domain.coupon.entity.Coupon;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.repository.CouponRepository;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService 단위 테스트")
class CouponServiceTest {

    @InjectMocks
    private CouponService couponService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private User testUser;
    private Coupon testCoupon;
    private CreateCouponRequest createCouponRequest;

    @BeforeEach
    void setUp() {
        // Test data setup
        testUser = User.builder()
                .email("test@example.com")
                .nickname("testUser")
                .role(UserRole.USER)
                .password("password")
                .build();
        // Reflection으로 ID 설정
        try {
            var field = User.class.getDeclaredField("userId");
            field.setAccessible(true);
            field.set(testUser, 1L);
        } catch (Exception e) {
            // ID 설정 실패 시 무시
        }

        testCoupon = Coupon.builder()
                .name("테스트 쿠폰")
                .discountAmount(5000)
                .totalQuantity(100)
                .startAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(7))
                .imageUrl("http://example.com/image.jpg")
                .build();

        createCouponRequest = CreateCouponRequest.builder()
                .name("테스트 쿠폰")
                .discountAmount(5000)
                .totalQuantity(100)
                .startAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(7))
                .imageUrl("http://example.com/image.jpg")
                .build();
    }

    @Nested
    @DisplayName("쿠폰 생성 테스트")
    class CreateCouponTest {

        @Test
        @DisplayName("성공: 쿠폰을 정상적으로 생성하고 Redis에 재고를 세팅한다")
        void createCoupon_Success() {
            // given
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);
            
            Coupon savedCoupon = Coupon.builder()
                    .name(createCouponRequest.getName())
                    .discountAmount(createCouponRequest.getDiscountAmount())
                    .totalQuantity(createCouponRequest.getTotalQuantity())
                    .startAt(createCouponRequest.getStartAt())
                    .expiredAt(createCouponRequest.getExpiredAt())
                    .imageUrl(createCouponRequest.getImageUrl())
                    .build();
            // Reflection으로 ID 설정
            try {
                var field = Coupon.class.getDeclaredField("couponId");
                field.setAccessible(true);
                field.set(savedCoupon, 1L);
            } catch (Exception e) {
                // ID 설정 실패 시 무시
            }

            given(couponRepository.save(any(Coupon.class))).willReturn(savedCoupon);

            // when
            CreateCouponResponse response = couponService.createCoupon(createCouponRequest);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo(createCouponRequest.getName());
            assertThat(response.getTotalQuantity()).isEqualTo(createCouponRequest.getTotalQuantity());

            // Redis 재고 세팅 검증
            verify(valueOperations).set(eq("coupon:stock:1"), eq("100"));
            verify(stringRedisTemplate).expire(eq("coupon:stock:1"), any(Duration.class));

            // 메트릭 초기화 검증
            verify(hashOperations, times(4)).put(eq("coupon:metrics:1"), anyString(), anyString());
            verify(stringRedisTemplate).expire(eq("coupon:metrics:1"), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("전체 쿠폰 조회 테스트")
    class GetAllCouponsTest {

        @Test
        @DisplayName("성공: 페이징 형식으로 쿠폰 목록을 반환한다")
        void getAllCoupons_Success() {
            // given
            PageRequest pageRequest = PageRequest.of(0, 10);
            List<Coupon> coupons = List.of(testCoupon);
            Slice<Coupon> couponSlice = new SliceImpl<>(coupons, pageRequest, false);

            given(couponRepository.findAllByOrderByCouponIdDesc(pageRequest)).willReturn(couponSlice);

            // when
            Slice<GetCouponResponse> result = couponService.getAllCoupons(pageRequest);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo(testCoupon.getName());
            assertThat(result.hasNext()).isFalse();
            
            verify(couponRepository).findAllByOrderByCouponIdDesc(pageRequest);
        }
    }

    @Nested
    @DisplayName("쿠폰 발급 테스트")
    class IssueCouponTest {

        @Test
        @DisplayName("성공: Redis에서 재고 차감 후 DB 동기화하고 UserCoupon을 저장한다")
        void issueCoupon_RedisSuccess() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);

            given(userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId))
                    .willReturn(false);
            given(couponRepository.findById(couponId)).willReturn(Optional.of(testCoupon));
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

            // Redis DECR 성공 시뮬레이션
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(Collections.singletonList("coupon:stock:1"))))
                    .willReturn(99L); // 재고 차감 후 99개 남음

            // DB 동기화 성공
            given(couponRepository.decrementRemainingQuantity(couponId)).willReturn(1);

            UserCoupon savedUserCoupon = UserCoupon.builder()
                    .user(testUser)
                    .coupon(testCoupon)
                    .build();
            given(userCouponRepository.save(any(UserCoupon.class))).willReturn(savedUserCoupon);

            // when
            IssueCouponResponse response = couponService.issueCoupon(userId, couponId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("쿠폰이 발급되었습니다!");

            // Redis 차감 검증
            verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), eq(Collections.singletonList("coupon:stock:1")));
            // DB 동기화 검증
            verify(couponRepository).decrementRemainingQuantity(couponId);
            // UserCoupon 저장 검증
            verify(userCouponRepository).save(any(UserCoupon.class));
            // 성공 메트릭 기록 검증
            verify(hashOperations, times(2)).increment(eq("coupon:metrics:1"), anyString(), eq(1L));
        }

        @Test
        @DisplayName("실패: 이미 발급된 쿠폰인 경우 BusinessException 발생")
        void issueCoupon_AlreadyIssued() {
            // given
            Long userId = 1L;
            Long couponId = 1L;

            given(userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_ALREADY_ISSUED);
            
            verify(couponRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("실패: 시작 전 쿠폰인 경우 BusinessException 발생")
        void issueCoupon_NotStarted() {
            // given
            Long userId = 1L;
            Long couponId = 1L;

            Coupon notStartedCoupon = Coupon.builder()
                    .name("미래 쿠폰")
                    .discountAmount(5000)
                    .totalQuantity(100)
                    .startAt(LocalDateTime.now().plusDays(1)) // 시작일이 미래
                    .expiredAt(LocalDateTime.now().plusDays(7))
                    .build();

            given(userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId))
                    .willReturn(false);
            given(couponRepository.findById(couponId)).willReturn(Optional.of(notStartedCoupon));

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_NOT_STARTED);
            
            verify(stringRedisTemplate, never()).execute(any(), anyList());
        }

        @Test
        @DisplayName("실패: 만료된 쿠폰인 경우 BusinessException 발생")
        void issueCoupon_Expired() {
            // given
            Long userId = 1L;
            Long couponId = 1L;

            Coupon expiredCoupon = Coupon.builder()
                    .name("만료된 쿠폰")
                    .discountAmount(5000)
                    .totalQuantity(100)
                    .startAt(LocalDateTime.now().minusDays(7))
                    .expiredAt(LocalDateTime.now().minusDays(1)) // 만료일이 과거
                    .build();

            given(userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId))
                    .willReturn(false);
            given(couponRepository.findById(couponId)).willReturn(Optional.of(expiredCoupon));

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_EXPIRED);
            
            verify(stringRedisTemplate, never()).execute(any(), anyList());
        }

        @Test
        @DisplayName("실패: 수량 소진된 쿠폰인 경우 BusinessException 발생")
        void issueCoupon_Exhausted() {
            // given
            Long userId = 1L;
            Long couponId = 1L;

            Coupon exhaustedCoupon = Coupon.builder()
                    .name("소진된 쿠폰")
                    .discountAmount(5000)
                    .totalQuantity(0) // 수량 0
                    .startAt(LocalDateTime.now().minusDays(1))
                    .expiredAt(LocalDateTime.now().plusDays(7))
                    .build();
            // remainingQuantity를 0으로 설정
            try {
                var field = Coupon.class.getDeclaredField("remainingQuantity");
                field.setAccessible(true);
                field.set(exhaustedCoupon, 0);
            } catch (Exception e) {
                // 설정 실패 시 무시
            }

            given(userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId))
                    .willReturn(false);
            given(couponRepository.findById(couponId)).willReturn(Optional.of(exhaustedCoupon));

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_EXHAUSTED);
            
            verify(stringRedisTemplate, never()).execute(any(), anyList());
        }

        @Test
        @DisplayName("Redis 재고 소진 시 BusinessException 발생")
        void issueCoupon_RedisStockExhausted() {
            // given
            Long userId = 1L;
            Long couponId = 1L;

            given(userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId))
                    .willReturn(false);
            given(couponRepository.findById(couponId)).willReturn(Optional.of(testCoupon));

            // Redis에서 재고 소진 응답 (-1)
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(Collections.singletonList("coupon:stock:1"))))
                    .willReturn(-1L);

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_EXHAUSTED);
            
            verify(couponRepository, never()).decrementRemainingQuantity(anyLong());
            verify(userCouponRepository, never()).save(any());
        }

        @Test
        @DisplayName("Fallback: Redis 장애 시 DB 비관적 락으로 재고 차감")
        void issueCoupon_DbFallback() {
            // given
            Long userId = 1L;
            Long couponId = 1L;
            
            given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);

            given(userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId))
                    .willReturn(false);
            given(couponRepository.findById(couponId)).willReturn(Optional.of(testCoupon));
            given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

            // Redis 장애 시뮬레이션
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(Collections.singletonList("coupon:stock:1"))))
                    .willThrow(new DataAccessException("Redis connection failed") {});

            // DB Fallback 성공
            given(couponRepository.findByIdWithLock(couponId)).willReturn(Optional.of(testCoupon));

            UserCoupon savedUserCoupon = UserCoupon.builder()
                    .user(testUser)
                    .coupon(testCoupon)
                    .build();
            given(userCouponRepository.save(any(UserCoupon.class))).willReturn(savedUserCoupon);

            // when
            IssueCouponResponse response = couponService.issueCoupon(userId, couponId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("쿠폰이 발급되었습니다!");

            // DB Fallback 사용 검증
            verify(couponRepository).findByIdWithLock(couponId);
            verify(userCouponRepository).save(any(UserCoupon.class));
            // Fallback 메트릭 기록 검증
            verify(hashOperations, times(2)).increment(eq("coupon:metrics:1"), anyString(), eq(1L));
            // Redis 동기화는 호출되지 않음
            verify(couponRepository, never()).decrementRemainingQuantity(anyLong());
        }

        @Test
        @DisplayName("실패: 쿠폰을 찾을 수 없는 경우 NotFoundException 발생")
        void issueCoupon_CouponNotFound() {
            // given
            Long userId = 1L;
            Long couponId = 999L;

            given(userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId))
                    .willReturn(false);
            given(couponRepository.findById(couponId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_NOT_FOUND);
            
            verify(stringRedisTemplate, never()).execute(any(), anyList());
        }

        @Test
        @DisplayName("실패: 사용자를 찾을 수 없는 경우 NotFoundException 발생")
        void issueCoupon_UserNotFound() {
            // given
            Long userId = 999L;
            Long couponId = 1L;

            given(userCouponRepository.existsByUserUserIdAndCouponCouponId(userId, couponId))
                    .willReturn(false);
            given(couponRepository.findById(couponId)).willReturn(Optional.of(testCoupon));

            // Redis 성공 시뮬레이션
            given(stringRedisTemplate.execute(any(DefaultRedisScript.class), eq(Collections.singletonList("coupon:stock:1"))))
                    .willReturn(99L);
            given(couponRepository.decrementRemainingQuantity(couponId)).willReturn(1);
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
            
            verify(userCouponRepository, never()).save(any());
        }
    }
}