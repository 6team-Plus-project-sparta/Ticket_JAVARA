package com.example.ticket_javara.domain.user.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CouponSearchCondition {
    // 프론트엔드에서 넘어올 기준: "EXPIRE"(임박), "LATEST"(최근 발급), "DISCOUNT"(할인 금액 높은 순) 등
    private String sortType;
}
