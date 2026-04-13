package com.example.ticket_javara.domain.booking.repository;

import com.example.ticket_javara.domain.booking.entity.Order;
import com.example.ticket_javara.domain.booking.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Order 레포지토리
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 내 주문 내역 조회 (인덱스: user_id) */
    Page<Order> findByUserUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findByUserUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status, Pageable pageable);
}
