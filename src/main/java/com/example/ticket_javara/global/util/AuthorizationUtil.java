package com.example.ticket_javara.global.util;

import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;

/**
 * 권한 검증 유틸리티 클래스
 * 하드코딩된 문자열 비교를 방지하고 타입 안전성을 보장합니다.
 */
public class AuthorizationUtil {

    /**
     * 관리자 권한 검증
     * @param userRole 사용자 역할 (문자열)
     * @throws ForbiddenException 관리자가 아닌 경우
     */
    public static void requireAdmin(String userRole) {
        if (!UserRole.ADMIN.name().equals(userRole)) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }
    }

    /**
     * 관리자 권한 검증 (Enum 버전)
     * @param userRole 사용자 역할 (Enum)
     * @throws ForbiddenException 관리자가 아닌 경우
     */
    public static void requireAdmin(UserRole userRole) {
        if (userRole != UserRole.ADMIN) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }
    }

    /**
     * 관리자 권한 확인
     * @param userRole 사용자 역할 (문자열)
     * @return 관리자 여부
     */
    public static boolean isAdmin(String userRole) {
        return UserRole.ADMIN.name().equals(userRole);
    }

    /**
     * 관리자 권한 확인 (Enum 버전)
     * @param userRole 사용자 역할 (Enum)
     * @return 관리자 여부
     */
    public static boolean isAdmin(UserRole userRole) {
        return userRole == UserRole.ADMIN;
    }
}