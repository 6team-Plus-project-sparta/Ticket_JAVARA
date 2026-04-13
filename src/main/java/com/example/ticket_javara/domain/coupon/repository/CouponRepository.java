package com.example.ticket_javara.domain.coupon.repository;

import com.example.ticket_javara.domain.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Coupon 레포지토리
 */
public interface CouponRepository extends JpaRepository<Coupon, Long> {
}
