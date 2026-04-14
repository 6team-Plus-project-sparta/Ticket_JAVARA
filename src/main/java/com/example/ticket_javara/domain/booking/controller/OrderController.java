package com.example.ticket_javara.domain.booking.controller;

import com.example.ticket_javara.domain.booking.dto.request.OrderCreateRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.OrderResponseDto;
import com.example.ticket_javara.domain.booking.service.OrderService;
import com.example.ticket_javara.global.common.ApiResponse;
import com.example.ticket_javara.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 주문 컨트롤러
 * API:
 *   POST /api/orders              — 주문 생성 (FN-BK-01) ⚠️
 *   GET  /api/orders/{orderId}    — 주문 상세 조회 (폴링 용)
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

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
            @Valid @RequestBody OrderCreateRequestDto request) {

        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[OrderController] 주문 생성 요청 userId={}, holdTokens={}", userId, request.getHoldTokens());

        OrderResponseDto response = orderService.createOrder(request, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
