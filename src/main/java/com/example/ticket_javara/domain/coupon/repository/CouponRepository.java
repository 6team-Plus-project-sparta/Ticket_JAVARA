package com.example.ticket_javara.domain.coupon.repository;

import com.example.ticket_javara.domain.coupon.entity.Coupon;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Coupon 레포지토리
 */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.couponId = :couponId")
    Optional<Coupon> findByIdWithLock(@Param("couponId") Long couponId);

    @Modifying
    @Transactional
    @Query("UPDATE Coupon c SET c.remainingQuantity = c.remainingQuantity - 1 WHERE c.couponId = :couponId AND c.remainingQuantity > 0")
    int decrementRemainingQuantity(@Param("couponId") Long couponId);

    Slice<Coupon> findAllByOrderByCouponIdDesc(Pageable pageable);
}
