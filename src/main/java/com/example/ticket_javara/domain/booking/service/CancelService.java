package com.example.ticket_javara.domain.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 주문 취소 서비스 (FN-BK-03) — 오케스트레이션 담당
 *
 * [트랜잭션 분리 구조]
 * 이 클래스는 오케스트레이션만 담당 (흐름 제어, Mock PG 환불 호출)
 * DB 트랜잭션 처리는 CancelTransactionService에 위임
 *
 * → Self-invocation 문제 해결:
 *   cancelOrder()에서 같은 클래스 내부의 @Transactional 메서드를 호출하면
 *   Spring AOP 프록시를 우회하여 트랜잭션이 작동하지 않음
 *   별도 Bean(CancelTransactionService)으로 분리하여 프록시 통한 트랜잭션 보장
 *
 * WebhookService → BookingConfirmService 분리와 동일한 패턴
 *
 * 처리 흐름:
 *   1. CancelTransactionService.execute() — DB 처리 (@Transactional, COMMIT)
 *   2. Mock PG 환불 요청 — 트랜잭션 외부 호출 (장애 전파 방지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelService {

    // ⭐ 트랜잭션 분리: DB 처리는 별도 Bean에 위임 (프록시를 통한 @Transactional 보장)
    private final CancelTransactionService cancelTransactionService;

    /**
     * 주문 취소 진입점 (FN-BK-03)
     *
     * @param orderId 취소할 주문 ID
     * @param userId  JWT에서 추출한 현재 사용자 ID
     */
    public void cancelOrder(Long orderId, Long userId) {

        // ① DB 처리 (별도 Bean 호출 → @Transactional 정상 작동, COMMIT 보장)
        cancelTransactionService.execute(orderId, userId);

        // ② Mock PG 환불 요청 (트랜잭션 외부 — 커넥션 고갈 방지)
        // [이유] @Transactional 안에서 외부 API 호출 시 DB 커넥션을 붙잡고 응답 대기
        // PG사 응답 지연 → DB 커넥션 고갈 → 서비스 전체 장애 (Cascading Failure)
        // 실제 연동 시에는 MQ(Message Queue) 또는 비동기 처리 권장
        log.info("[CancelService] Mock PG 환불 요청 orderId={}", orderId);
        log.info("[CancelService] 주문 취소 완료 orderId={}, userId={}", orderId, userId);
    }
}