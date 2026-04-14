package com.example.ticket_javara.domain.user.dto.response;

import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.entity.UserCouponStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserCouponResponse {

    private Long userCouponId;
    private Long couponId;
    private String name;
    private Integer discountAmount;
    private UserCouponStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expiredAt;

    public static UserCouponResponse from(UserCoupon userCoupon) {
        return UserCouponResponse.builder()
                .userCouponId(userCoupon.getUserCouponId())
                .couponId(userCoupon.getCoupon().getCouponId())
                .name(userCoupon.getCoupon().getName())
                .discountAmount(userCoupon.getCoupon().getDiscountAmount())
                .status(userCoupon.getStatus())
                .issuedAt(userCoupon.getIssuedAt())
                .usedAt(userCoupon.getUsedAt())
                .expiredAt(userCoupon.getCoupon().getExpiredAt())
                .build();
    }
}
