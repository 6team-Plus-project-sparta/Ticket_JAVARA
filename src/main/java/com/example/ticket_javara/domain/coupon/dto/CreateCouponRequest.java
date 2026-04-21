package com.example.ticket_javara.domain.coupon.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCouponRequest {
    @NotBlank
    private String name;
    
    @NotNull
    @Min(1)
    private Integer discountAmount;
    
    @NotNull
    @Min(1)
    private Integer totalQuantity;
    
    @NotNull
    private LocalDateTime startAt;
    
    @NotNull
    private LocalDateTime expiredAt;

    private String imageUrl;

    @AssertTrue(message = "시작일은 만료일보다 이전이어야 합니다.")
    public boolean isValidDateRange() {
        if (startAt == null || expiredAt == null) return true;
        return startAt.isBefore(expiredAt);
    }
}
