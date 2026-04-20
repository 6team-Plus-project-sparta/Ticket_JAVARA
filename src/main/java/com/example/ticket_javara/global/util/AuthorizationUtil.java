package com.example.ticket_javara.global.util;

import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 권한 검증 유틸리티 클래스
 * Spring Security의 GrantedAuthority를 기반으로 타입 안전성을 보장합니다.
 */
public class AuthorizationUtil {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String ADMIN_AUTHORITY = ROLE_PREFIX + UserRole.ADMIN.name();

    /**
     * 현재 인증된 사용자가 관리자인지 확인 (Spring Security 기반)
     * @return 관리자 여부
     */
    public static boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }

    /**
     * 현재 인증된 사용자의 관리자 권한 검증
     * @throws ForbiddenException 관리자가 아닌 경우
     */
    public static void requireCurrentUserAdmin() {
        if (!isCurrentUserAdmin()) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }
    }

    /**
     * 관리자 권한 검증 (Enum 버전) - 타입 안전
     * @param userRole 사용자 역할 (Enum)
     * @throws ForbiddenException 관리자가 아닌 경우
     */
    public static void requireAdmin(UserRole userRole) {
        if (userRole != UserRole.ADMIN) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }
    }

    /**
     * 관리자 권한 확인 (Enum 버전) - 타입 안전
     * @param userRole 사용자 역할 (Enum)
     * @return 관리자 여부
     */
    public static boolean isAdmin(UserRole userRole) {
        return userRole == UserRole.ADMIN;
    }

    /**
     * 레거시 호환성을 위한 문자열 버전 (점진적 마이그레이션용)
     * @deprecated Spring Security 기반 메서드 사용 권장
     */
    @Deprecated
    public static void requireAdmin(String userRole) {
        if (userRole == null) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }
        try {
            UserRole role = UserRole.valueOf(userRole);
            requireAdmin(role);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }
    }

    /**
     * 레거시 호환성을 위한 문자열 버전 (점진적 마이그레이션용)
     * @deprecated Spring Security 기반 메서드 사용 권장
     */
    @Deprecated
    public static boolean isAdmin(String userRole) {
        if (userRole == null) {
            return false;
        }
        try {
            UserRole role = UserRole.valueOf(userRole);
            return isAdmin(role);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}