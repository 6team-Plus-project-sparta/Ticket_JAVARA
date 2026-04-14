package com.example.ticket_javara.domain.booking.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 주문 생성 요청 DTO (FN-BK-01)
 * holdTokens: 최대 4개 (FN-SEAT-02에서 발급된 UUID)
 * userCouponId: 적용할 쿠폰 (선택, USER_COUPON 테이블 ID)
 */
@Getter
@NoArgsConstructor
public class OrderCreateRequestDto {

    @NotEmpty(message = "Hold 토큰은 최소 1개 이상이어야 합니다.")
    @Size(max = 4, message = "최대 4석까지 동시에 예매할 수 있습니다.")
    private List<String> holdTokens;

    /** 적용할 쿠폰 ID (선택) — USER_COUPON.user_coupon_id */
    private Long userCouponId;
}
