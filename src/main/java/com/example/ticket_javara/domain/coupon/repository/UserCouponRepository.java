package com.example.ticket_javara.domain.coupon.repository;

import com.example.ticket_javara.domain.coupon.entity.UserCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * UserCoupon 레포지토리
 */
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    /** 중복 발급 체크 */
    boolean existsByUserUserIdAndCouponCouponId(Long userId, Long couponId);

    /**
     * 비관적 락으로 UserCoupon 조회 (취소 시 쿠폰 복원 충돌 방지 — C-03 대응)
     * 복원 중 동일 쿠폰 재사용 시도와의 충돌 방지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userCouponId = :userCouponId")
    Optional<UserCoupon> findByIdWithLock(@Param("userCouponId") Long userCouponId);

    List<UserCoupon> findByUserUserId(Long userId);
    
    /** 특정 쿠폰의 발급된 UserCoupon 목록 조회 (테스트용) */
    List<UserCoupon> findByCouponCouponId(Long couponId);
}