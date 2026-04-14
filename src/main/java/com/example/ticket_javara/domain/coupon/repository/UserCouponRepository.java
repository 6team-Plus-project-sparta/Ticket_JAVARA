package com.example.ticket_javara.domain.coupon.repository;

import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import com.example.ticket_javara.domain.coupon.entity.UserCouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * UserCoupon 레포지토리
 */
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    /** 중복 발급 체크 (락 획득 전 선행 체크) */
    boolean existsByUserUserIdAndCouponCouponId(Long userId, Long couponId);

    /** 내 쿠폰 목록 조회 */
    List<UserCoupon> findByUserUserIdAndStatus(Long userId, UserCouponStatus status);

    List<UserCoupon> findByUserUserId(Long userId);

    /**
     * 비관적 락으로 UserCoupon 조회 (쿠폰 복원 시 취소 충돌 방지 — C-03 대응)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userCouponId = :userCouponId")
    Optional<UserCoupon> findByIdWithLock(@Param("userCouponId") Long userCouponId);
}
