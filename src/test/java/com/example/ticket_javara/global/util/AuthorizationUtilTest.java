package com.example.ticket_javara.global.util;

import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.global.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AuthorizationUtilTest {

    @Test
    @DisplayName("관리자 권한 검증 - 성공")
    void requireAdmin_Success() {
        // given & when & then
        assertThatCode(() -> AuthorizationUtil.requireAdmin("ADMIN"))
                .doesNotThrowAnyException();
        
        assertThatCode(() -> AuthorizationUtil.requireAdmin(UserRole.ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("관리자 권한 검증 - 실패 (일반 사용자)")
    void requireAdmin_Fail_User() {
        // given & when & then
        assertThatThrownBy(() -> AuthorizationUtil.requireAdmin("USER"))
                .isInstanceOf(ForbiddenException.class);
        
        assertThatThrownBy(() -> AuthorizationUtil.requireAdmin(UserRole.USER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("관리자 권한 검증 - 실패 (잘못된 문자열)")
    void requireAdmin_Fail_InvalidString() {
        // given & when & then
        assertThatThrownBy(() -> AuthorizationUtil.requireAdmin("ADMIM")) // 오타
                .isInstanceOf(ForbiddenException.class);
        
        assertThatThrownBy(() -> AuthorizationUtil.requireAdmin("admin")) // 소문자
                .isInstanceOf(ForbiddenException.class);
        
        // null 테스트는 명시적으로 String 타입으로 캐스팅
        assertThatThrownBy(() -> AuthorizationUtil.requireAdmin((String) null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("관리자 권한 확인 - 문자열 버전")
    void isAdmin_String() {
        // given & when & then
        assertThat(AuthorizationUtil.isAdmin("ADMIN")).isTrue();
        assertThat(AuthorizationUtil.isAdmin("USER")).isFalse();
        assertThat(AuthorizationUtil.isAdmin("admin")).isFalse(); // 대소문자 구분
        assertThat(AuthorizationUtil.isAdmin("ADMIM")).isFalse(); // 오타
        // null 테스트는 명시적으로 String 타입으로 캐스팅
        assertThat(AuthorizationUtil.isAdmin((String) null)).isFalse();
    }

    @Test
    @DisplayName("관리자 권한 확인 - Enum 버전")
    void isAdmin_Enum() {
        // given & when & then
        assertThat(AuthorizationUtil.isAdmin(UserRole.ADMIN)).isTrue();
        assertThat(AuthorizationUtil.isAdmin(UserRole.USER)).isFalse();
    }
}