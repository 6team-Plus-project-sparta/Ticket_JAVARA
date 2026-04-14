package com.example.ticket_javara.domain.coupon.dto;

import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class IssueCouponResponse {
    private String message;
    private Long userCouponId;
    private String couponName;
    private Integer discountAmount;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiredAt;

    public static IssueCouponResponse from(UserCoupon userCoupon, String message) {
        return IssueCouponResponse.builder()
                .message(message)
                .userCouponId(userCoupon.getUserCouponId())
                .couponName(userCoupon.getCoupon().getName())
                .discountAmount(userCoupon.getCoupon().getDiscountAmount())
                .expiredAt(userCoupon.getCoupon().getExpiredAt())
                .build();
    }
}
