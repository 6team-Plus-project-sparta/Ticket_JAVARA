package com.example.ticket_javara.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * SecurityContext의 principal — UserDetails 구현체
 * JwtAuthFilter에서 생성되며, SecurityUtil에서 꺼낸다.
 * 패키지 구조 설계서 §2 global/security/CustomUserDetails.java 참조
 *
 * Refresh Token 미구현(ADR-001) → DB 조회 없이 토큰 클레임만으로 구성
 * 보관 정보: userId, email, role
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String role;

    public CustomUserDetails(Long userId, String email, String role) {
        this.userId = userId;
        this.email = email;
        this.role = role;
    }

    /**
     * "ROLE_" 접두사를 붙여 Spring Security 권한 형식으로 반환
     * ex) role="ADMIN" → SimpleGrantedAuthority("ROLE_ADMIN")
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    /** JWT 방식이므로 비밀번호 불필요 */
    @Override
    public String getPassword() { return null; }

    /** email을 username으로 사용 */
    @Override
    public String getUsername() { return email; }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
