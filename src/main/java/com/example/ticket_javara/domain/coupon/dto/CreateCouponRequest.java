package com.example.ticket_javara.domain.coupon.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Getter
@NoArgsConstructor
public class CreateCouponRequest {
    @NotBlank
    private String name;
    
    @NotNull
    private Integer discountAmount;
    
    @NotNull
    private Integer totalQuantity;
    
    @NotNull
    private LocalDateTime startAt;
    
    @NotNull
    private LocalDateTime expiredAt;
}
