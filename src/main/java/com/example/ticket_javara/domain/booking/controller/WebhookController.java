package com.example.ticket_javara.domain.booking.controller;

import com.example.ticket_javara.domain.booking.dto.request.WebhookRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.WebhookResponseDto;
import com.example.ticket_javara.domain.booking.service.WebhookService;
import com.example.ticket_javara.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Mock PG 웹훅 컨트롤러
 * API:
 *   POST /api/mock-pg/webhook — 결제 완료 웹훅 수신 (FN-BK-02)
 *
 * 🔓 인증 불필요 (PG 서버에서 호출) — SecurityConfig 공개 URL 목록에 추가 필요
 * 멱등성: orderId 기준, 이미 처리된 주문은 재처리하지 않음
 * 웹훅은 성공/실패 모두 200 OK 반환 (PG 계약)
 */
@Slf4j
@RestController
@RequestMapping("/api/mock-pg")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * POST /api/mock-pg/webhook
     * Mock PG → 서버로 결제 결과 웹훅 전달 (FN-BK-02)
     *
     * 성공(SUCCESS): ORDER/BOOKING → CONFIRMED, ACTIVE_BOOKING INSERT, 쿠폰 사용 처리
     * 실패(FAIL):    ORDER/BOOKING → FAILED (Hold 유지)
     *
     * @return 200 OK (항상 — PG 웹훅 계약)
     */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<WebhookResponseDto>> receiveWebhook(
            @Valid @RequestBody WebhookRequestDto request) {

        log.info("[WebhookController] 웹훅 수신 orderId={}, status={}",
                request.getOrderId(), request.getPaymentStatus());

        WebhookResponseDto response = webhookService.processWebhook(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
