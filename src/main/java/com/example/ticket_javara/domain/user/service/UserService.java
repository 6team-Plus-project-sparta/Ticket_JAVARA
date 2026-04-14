package com.example.ticket_javara.domain.user.service;

import com.example.ticket_javara.domain.user.dto.request.UserUpdateRequest;
import com.example.ticket_javara.domain.user.dto.response.UserResponse;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.entity.OrderStatus;
import com.example.ticket_javara.domain.booking.repository.OrderRepository;
import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.repository.UserCouponRepository;
import com.example.ticket_javara.domain.user.dto.response.OrderSummaryResponse;
import com.example.ticket_javara.domain.user.dto.response.UserCouponResponse;
import com.example.ticket_javara.global.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.example.ticket_javara.domain.user.dto.request.CouponSearchCondition;
import com.example.ticket_javara.domain.coupon.entity.UserCouponStatus;
import com.example.ticket_javara.global.exception.ConflictException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderRepository orderRepository;
    private final UserCouponRepository userCouponRepository;

    /** 내 정보 조회 (FN-AUTH-03) */
    public UserResponse getMyInfo(Long userId) {
        User user = getUser(userId);
        return UserResponse.from(user);
    }

    /** 내 정보 수정 (FN-AUTH-04) */
    @Transactional
    public void updateMyInfo(Long userId, UserUpdateRequest request) {
        User user = getUser(userId);

        // 닉네임 변경
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            if (!user.getNickname().equals(request.getNickname()) && 
                userRepository.existsByNickname(request.getNickname())) {
                throw new ConflictException(ErrorCode.NICKNAME_DUPLICATED);
            }
            user.updateNickname(request.getNickname());
        }

        // 비밀번호 변경
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            // 현재 비밀번호 값 누락 및 공백 사전 차단 (보안 취약점 및 불필요한 Bcrypt 연산 방지)
            if (request.getCurrentPassword() == null || request.getCurrentPassword().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
            }

            // 현재 비밀번호 검증
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
            }
            String encodedNewPassword = passwordEncoder.encode(request.getPassword());
            user.updatePassword(encodedNewPassword);
        }
    }

    /** 내 예약(주문) 내역 조회 (FN-BK-04) */
    public Page<OrderSummaryResponse> getMyBookings(Long userId, OrderStatus status, Pageable pageable) {
        getUser(userId); // 유저 존재 여부 검증
        
        Page<Order> orders;
        if (status != null) {
            orders = orderRepository.findByUserUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        } else {
            orders = orderRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        
        return orders.map(OrderSummaryResponse::from);
    }

    /** 내 쿠폰 목록 조회 (FN-CPN-03) */
    public List<UserCouponResponse> getMyCoupons(Long userId, CouponSearchCondition condition) {
        getUser(userId); // 유저 존재 여부 검증
        
        List<UserCoupon> coupons = userCouponRepository.findByUserUserId(userId);
        
        LocalDateTime now = LocalDateTime.now();
        Stream<UserCoupon> stream = coupons.stream()
                // 사용 가능(ISSUED) 상태이고 만료되지 않은 쿠폰만 필터링
                .filter(uc -> uc.getStatus() == UserCouponStatus.ISSUED 
                           && uc.getCoupon().getExpiredAt().isAfter(now));

        // 프론트가 보낸 스트링 정렬 분기
        Comparator<UserCoupon> comparator;
        String sortType = (condition != null && condition.getSortType() != null) ? condition.getSortType() : "";
        
        if ("LATEST".equalsIgnoreCase(sortType)) {
            // 최근 발급순 (내림차순)
            comparator = Comparator.comparing(UserCoupon::getIssuedAt).reversed();
        } else if ("DISCOUNT".equalsIgnoreCase(sortType)) {
            // 할인액(할인율) 높은 순 (내림차순)
            comparator = Comparator.comparing((UserCoupon uc) -> uc.getCoupon().getDiscountAmount()).reversed();
        } else { 
            // 만료 임박순 (오름차순) - 기본값
            comparator = Comparator.comparing(uc -> uc.getCoupon().getExpiredAt());
        }
        
        return stream.sorted(comparator)
                .map(UserCouponResponse::from)
                .collect(Collectors.toList());
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }
}
