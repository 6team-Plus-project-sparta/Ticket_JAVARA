package com.example.ticket_javara.domain.booking.service;

import com.example.ticket_javara.domain.booking.dto.request.TossPaymentRequestDto;
import com.example.ticket_javara.domain.booking.dto.request.WebhookRequestDto;
import com.example.ticket_javara.domain.booking.dto.response.TossPaymentResponseDto;
import com.example.ticket_javara.domain.booking.entity.Booking;
import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.entity.OrderStatus;
import com.example.ticket_javara.domain.booking.repository.BookingRepository;
import com.example.ticket_javara.domain.booking.repository.OrderRepository;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.InvalidRequestException;
import com.example.ticket_javara.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 토스페이먼츠 결제 승인 서비스
 *
 * [트랜잭션 설계]
 * - 이 메서드는 @Transactional 없음
 * - 외부 트랜잭션이 없어야 REQUIRES_NEW(confirmOrder)가 락 충돌 없이 동작
 * - 멱등성은 confirmOrder 내부의 CONFIRMED 상태 체크로 보장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossPaymentService {

    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final BookingConfirmService bookingConfirmService;

    @Value("${toss.secret-key}")
    private String tossSecretKey;

    private static final String TOSS_CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

    public TossPaymentResponseDto confirmPayment(Long orderId, TossPaymentRequestDto dto, Long userId) {

        // 1. ORDER 조회 및 소유자 검증 (락 없이 조회 — confirmOrder 내부에서 처리)
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));

        order.validateOwner(userId, ErrorCode.ORDER_NOT_OWNED);

        // 이미 확정된 주문 → 멱등 처리
        if (OrderStatus.CONFIRMED.equals(order.getStatus())) {
            log.info("[TossPaymentService] 이미 확정된 주문 orderId={}", orderId);
            return new TossPaymentResponseDto(orderId, dto.getPaymentKey(), OrderStatus.CONFIRMED);
        }

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new InvalidRequestException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        // 2. 결제 금액 검증
        if (!order.getFinalAmount().equals(dto.getAmount())) {
            log.error("[TossPaymentService] 결제 금액 불일치 orderId={}, expected={}, actual={}",
                    orderId, order.getFinalAmount(), dto.getAmount());
            bookingConfirmService.failOrder(orderId);
            throw new InvalidRequestException(ErrorCode.VALIDATION_FAILED);
        }

        // 3. 토스 승인 요청
        String encodedKey = Base64.getEncoder()
                .encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        try {
            WebClient webClient = WebClient.builder()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> tossRequestBody = Map.of(
                    "paymentKey", dto.getPaymentKey(),
                    "orderId",    dto.getTossOrderId(),
                    "amount",     dto.getAmount()
            );

            webClient.post()
                    .uri(TOSS_CONFIRM_URL)
                    .bodyValue(tossRequestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.info("[TossPaymentService] 토스 승인 성공 orderId={}", orderId);

            // 4. DB 확정 처리 (REQUIRES_NEW 별도 트랜잭션 — 내부에서 락 + 멱등 처리)
            List<Booking> bookings = bookingRepository.findByOrderOrderId(orderId);
            WebhookRequestDto webhookDto = WebhookRequestDto.ofTossSuccess(
                    orderId, dto.getPaymentKey(), dto.getAmount());
            bookingConfirmService.confirmOrder(order, bookings, webhookDto);

            return new TossPaymentResponseDto(orderId, dto.getPaymentKey(), OrderStatus.CONFIRMED);

        } catch (WebClientResponseException e) {
            log.error("[TossPaymentService] 토스 승인 실패 orderId={}, status={}, body={}",
                    orderId, e.getStatusCode(), e.getResponseBodyAsString());

            // 토스 에러지만 이미 confirmOrder가 성공한 경우 (두 번째 동시 요청)
            Order currentOrder = orderRepository.findById(orderId).orElse(null);
            if (currentOrder != null && OrderStatus.CONFIRMED.equals(currentOrder.getStatus())) {
                log.info("[TossPaymentService] 토스 에러지만 이미 확정된 주문 orderId={}", orderId);
                return new TossPaymentResponseDto(orderId, dto.getPaymentKey(), OrderStatus.CONFIRMED);
            }

            bookingConfirmService.failOrder(orderId);
            throw new InvalidRequestException(ErrorCode.VALIDATION_FAILED);
        }
    }
}