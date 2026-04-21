package com.example.ticket_javara.domain.booking.controller;

import com.example.ticket_javara.domain.booking.dto.request.OrderCreateRequestDto;
import com.example.ticket_javara.domain.booking.dto.request.TossPaymentRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.OrderDetailResponseDto;
import com.example.ticket_javara.domain.booking.dto.response.OrderResponseDto;
import com.example.ticket_javara.domain.booking.dto.response.TossPaymentResponseDto;
import com.example.ticket_javara.domain.booking.service.CancelService;
import com.example.ticket_javara.domain.booking.service.OrderDetailService;
import com.example.ticket_javara.domain.booking.service.OrderService;
import com.example.ticket_javara.domain.booking.service.TossPaymentService;
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
 *   POST /api/orders                            — 주문 생성 (FN-BK-01)
 *   GET  /api/orders/{orderId}                  — 주문 상세 조회 (FN-BK-06)
 *   POST /api/orders/{orderId}/cancel           — 주문 취소 (FN-BK-03)
 *   POST /api/orders/{orderId}/confirm-payment  — 토스페이먼츠 결제 승인
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderDetailService orderDetailService;
    private final CancelService cancelService;
    private final TossPaymentService tossPaymentService;

    /**
     * POST /api/orders
     * 주문 생성 (FN-BK-01)
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
     * 결제 후 PENDING → CONFIRMED 폴링 용도로도 사용 가능
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

    /**
     * POST /api/orders/{orderId}/confirm-payment
     * 토스페이먼츠 결제 승인
     *
     * 프론트에서 토스 결제창 완료 후 호출
     * 서버가 토스 API에 최종 승인 요청 후 ORDER/BOOKING CONFIRMED 처리
     *
     * @return 200 OK { orderId, paymentKey, status: CONFIRMED }
     */
    @PostMapping("/{orderId}/confirm-payment")
    public ResponseEntity<ApiResponse<TossPaymentResponseDto>> confirmPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody TossPaymentRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        log.info("[OrderController] 토스 결제 승인 요청 orderId={}, userId={}", orderId, userId);

        TossPaymentResponseDto response = tossPaymentService.confirmPayment(orderId, request, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}