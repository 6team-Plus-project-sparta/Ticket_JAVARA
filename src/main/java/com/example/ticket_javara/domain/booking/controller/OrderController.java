package com.example.ticket_javara.domain.booking.controller;

import com.example.ticket_javara.domain.booking.dto.request.OrderCreateRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.OrderDetailResponseDto;
import com.example.ticket_javara.domain.booking.dto.response.OrderResponseDto;
import com.example.ticket_javara.domain.booking.service.CancelService;
import com.example.ticket_javara.domain.booking.service.OrderDetailService;
import com.example.ticket_javara.domain.booking.service.OrderService;
import com.example.ticket_javara.global.common.ApiResponse;
import com.example.ticket_javara.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 주문 컨트롤러
 * API:
 *   POST /api/orders                    — 주문 생성 (FN-BK-01)
 *   GET  /api/orders/{orderId}          — 주문 상세 조회 (FN-BK-06)
 *   POST /api/orders/{orderId}/cancel   — 주문 취소 (FN-BK-03)
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderDetailService orderDetailService;
    private final CancelService cancelService;

    /**
     * POST /api/orders
     * 주문 생성 (FN-BK-01)
     *
     * holdTokens로 Hold 검증 후 ORDER + BOOKING 생성
     * Mock PG에 결제 요청 (로그 출력)
     * 성공 시 Hold TTL +5분 연장 (L-02 대응)
     *
     * @return 200 OK { orderId, status: PENDING, finalAmount, ... }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponseDto>> createOrder(
            @Valid @RequestBody OrderCreateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        log.info("[OrderController] 주문 생성 요청 userId={}, holdTokens={}", userId, request.getHoldTokens());

        OrderResponseDto response = orderService.createOrder(request, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/orders/{orderId}
     * 주문 상세 조회 (FN-BK-06)
     *
     * 주문 생성(PENDING) 후 결제 결과 폴링 용도로도 사용 가능
     * ORDER + BOOKING 목록 + PAYMENT + 쿠폰 정보 한번에 반환
     *
     * @return 200 OK OrderDetailResponseDto
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailResponseDto>> getOrderDetail(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        log.info("[OrderController] 주문 상세 조회 orderId={}, userId={}", orderId, userId);

        OrderDetailResponseDto response = orderDetailService.getOrderDetail(orderId, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/orders/{orderId}/cancel
     * 주문 취소 (FN-BK-03)
     *
     * CONFIRMED 상태 주문만 취소 가능
     * 이벤트 시작 24시간 전까지만 취소 가능 (KST 기준)
     * ACTIVE_BOOKING DELETE → BOOKING/ORDER CANCELLED → 쿠폰 복원 → Mock PG 환불
     *
     * @return 200 OK
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        log.info("[OrderController] 주문 취소 요청 orderId={}, userId={}", orderId, userId);

        cancelService.cancelOrder(orderId, userId);

        return ResponseEntity.ok(ApiResponse.success(null, "주문이 취소되었습니다."));
    }
}