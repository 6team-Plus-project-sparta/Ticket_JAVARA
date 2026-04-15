package com.example.ticket_javara.domain.coupon.dto;

import com.example.ticket_javara.domain.coupon.entity.Coupon;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class GetCouponResponse {
    private Long couponId;
    private String name;
    private Integer discountAmount;
    private Integer totalQuantity;
    private Integer remainingQuantity;
    private String imageUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiredAt;

    public static GetCouponResponse from(Coupon coupon) {
        return GetCouponResponse.builder()
                .couponId(coupon.getCouponId())
                .name(coupon.getName())
                .discountAmount(coupon.getDiscountAmount())
                .totalQuantity(coupon.getTotalQuantity())
                .remainingQuantity(coupon.getRemainingQuantity())
                .imageUrl(coupon.getImageUrl())
                .startAt(coupon.getStartAt())
                .expiredAt(coupon.getExpiredAt())
                .build();
    }
}
