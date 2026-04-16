package com.example.ticket_javara.domain.coupon.dto;

import com.example.ticket_javara.domain.coupon.entity.Coupon;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CreateCouponResponse {
    private Long couponId;
    private String name;
    private Integer totalQuantity;
    private Integer remainingQuantity;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiredAt;

    private String imageUrl;

    public static CreateCouponResponse from(Coupon coupon) {
        return CreateCouponResponse.builder()
                .couponId(coupon.getCouponId())
                .name(coupon.getName())
                .totalQuantity(coupon.getTotalQuantity())
                .remainingQuantity(coupon.getRemainingQuantity())
                .startAt(coupon.getStartAt())
                .expiredAt(coupon.getExpiredAt())
                .imageUrl(coupon.getImageUrl())
                .build();
    }
}
