package com.example.ticket_javara.domain.coupon.controller;

import com.example.ticket_javara.domain.coupon.dto.CreateCouponRequest;
import com.example.ticket_javara.domain.coupon.dto.CreateCouponResponse;
import com.example.ticket_javara.domain.coupon.dto.IssueCouponResponse;
import com.example.ticket_javara.domain.coupon.service.CouponService;
import com.example.ticket_javara.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * 쿠폰 통계 및 등록
     * ADMIN 권한 확인 (서비스 단에서 처리/확인)
     */
    @PostMapping("/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreateCouponResponse> createCoupon(
            @Valid @RequestBody CreateCouponRequest request) {
        CreateCouponResponse response = couponService.createCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 선착순 쿠폰 발급 (유저 단)
     */
    @PostMapping("/coupons/{couponId}/issue")
    public ResponseEntity<IssueCouponResponse> issueCoupon(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long couponId) {
        IssueCouponResponse response = couponService.issueCoupon(userDetails.getUserId(), couponId);
        return ResponseEntity.ok(response);
    }

    /**
     * 전체 쿠폰 목록 조회 (무한 스크롤)
     */
    @GetMapping("/coupons")
    public ResponseEntity<org.springframework.data.domain.Slice<com.example.ticket_javara.domain.coupon.dto.GetCouponResponse>> getAllCoupons(
            @org.springframework.data.web.PageableDefault(size = 10) org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Slice<com.example.ticket_javara.domain.coupon.dto.GetCouponResponse> response = couponService.getAllCoupons(pageable);
        return ResponseEntity.ok(response);
    }
}
