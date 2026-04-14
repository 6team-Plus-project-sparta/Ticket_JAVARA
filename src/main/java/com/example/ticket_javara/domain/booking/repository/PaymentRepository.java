package com.example.ticket_javara.domain.booking.repository;

import com.example.ticket_javara.domain.booking.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * PAYMENT 레포지토리
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderOrderId(Long orderId);
}
