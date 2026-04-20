package com.example.ticket_javara.global.util;

import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.global.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AuthorizationUtilTest {

    @Test
    @DisplayName("Enum 기반 관리자 권한 확인")
    void isAdmin_Enum() {
        // given & when & then
        assertThat(AuthorizationUtil.isAdmin(UserRole.ADMIN)).isTrue();
        assertThat(AuthorizationUtil.isAdmin(UserRole.USER)).isFalse();
    }

    @Test
    @DisplayName("Enum 기반 관리자 권한 검증 - 성공")
    void requireAdmin_Enum_Success() {
        // given & when & then
        assertThatCode(() -> AuthorizationUtil.requireAdmin(UserRole.ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Enum 기반 관리자 권한 검증 - 실패")
    void requireAdmin_Enum_Fail() {
        // given & when & then
        assertThatThrownBy(() -> AuthorizationUtil.requireAdmin(UserRole.USER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("레거시 문자열 기반 권한 확인 (Deprecated)")
    void isAdmin_String_Legacy() {
        // given & when & then
        assertThat(AuthorizationUtil.isAdmin("ADMIN")).isTrue();
        assertThat(AuthorizationUtil.isAdmin("USER")).isFalse();
        assertThat(AuthorizationUtil.isAdmin("INVALID")).isFalse();
        assertThat(AuthorizationUtil.isAdmin((String) null)).isFalse();
    }

    @Test
    @DisplayName("레거시 문자열 기반 권한 검증 (Deprecated)")
    void requireAdmin_String_Legacy() {
        // given & when & then
        assertThatCode(() -> AuthorizationUtil.requireAdmin("ADMIN"))
                .doesNotThrowAnyException();
        
        assertThatThrownBy(() -> AuthorizationUtil.requireAdmin("USER"))
                .isInstanceOf(ForbiddenException.class);
        
        assertThatThrownBy(() -> AuthorizationUtil.requireAdmin("INVALID"))
                .isInstanceOf(ForbiddenException.class);
    }
}