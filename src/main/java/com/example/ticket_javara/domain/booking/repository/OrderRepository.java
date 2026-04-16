package com.example.ticket_javara.domain.booking.repository;

import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Order 레포지토리
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 내 주문 내역 조회 (인덱스: user_id) */
    Page<Order> findByUserUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findByUserUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status, Pageable pageable);

    /**
     * 비관적 락으로 주문 조회 (취소/확정 충돌 방지 — L-04, C-03 대응)
     * 취소 트랜잭션과 웹훅 확정 트랜잭션의 동시 실행 방지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId")
    Optional<Order> findByIdWithLock(@Param("orderId") Long orderId);
}