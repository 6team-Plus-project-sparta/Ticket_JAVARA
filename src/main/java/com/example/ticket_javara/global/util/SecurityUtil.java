package com.example.ticket_javara.global.util;

import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import com.example.ticket_javara.global.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContext에서 현재 인증된 사용자 정보를 추출하는 유틸리티
 * JwtAuthFilter에서 principal로 CustomUserDetails를 주입한다.
 */
public class SecurityUtil {

    private SecurityUtil() {}

    /**
     * 현재 인증된 사용자의 userId 반환
     * 인증 정보가 없으면 ForbiddenException 발생
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return ((CustomUserDetails) authentication.getPrincipal()).getUserId();
    }

    /**
     * 현재 인증된 사용자의 email 반환
     * 인증 정보가 없으면 ForbiddenException 발생
     */
    public static String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return ((CustomUserDetails) authentication.getPrincipal()).getEmail();
    }

    /**
     * 현재 인증된 사용자의 role 반환
     * authorities에서 꺼내므로 CustomUserDetails 캐스팅 불필요
     */
    public static String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        // "ROLE_ADMIN" → "ADMIN"
        return authentication.getAuthorities().iterator().next()
                .getAuthority().replace("ROLE_", "");
    }
}
