package com.example.ticket_javara.domain.user.controller;

import com.example.ticket_javara.domain.user.dto.request.CouponSearchCondition;
import com.example.ticket_javara.domain.user.dto.request.UserUpdateRequest;
import com.example.ticket_javara.domain.booking.entity.OrderStatus;
import com.example.ticket_javara.domain.user.dto.response.OrderSummaryResponse;
import com.example.ticket_javara.domain.user.dto.response.UserCouponResponse;
import com.example.ticket_javara.domain.user.dto.response.UserResponse;
import com.example.ticket_javara.domain.user.service.UserService;
import com.example.ticket_javara.global.common.ApiResponse;
import com.example.ticket_javara.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 내 정보 조회 (FN-AUTH-03) */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = userService.getMyInfo(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 내 정보 수정 (FN-AUTH-04) */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UserUpdateRequest request) {
        userService.updateMyInfo(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 내 예매(주문) 내역 조회 (FN-BK-04) */
    @GetMapping("/me/bookings")
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> getMyBookings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(page = 0, size = 10) Pageable pageable) {
        Page<OrderSummaryResponse> response = userService.getMyBookings(userDetails.getUserId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 내 쿠폰 목록 조회 (FN-CPN-03) */
    @GetMapping("/me/coupons")
    public ResponseEntity<ApiResponse<List<UserCouponResponse>>> getMyCoupons(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute CouponSearchCondition condition) {
        List<UserCouponResponse> response = userService.getMyCoupons(userDetails.getUserId(), condition);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
