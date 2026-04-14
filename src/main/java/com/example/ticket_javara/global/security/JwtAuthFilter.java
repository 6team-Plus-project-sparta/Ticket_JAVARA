package com.example.ticket_javara.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터
 * Authorization 헤더에서 Bearer 토큰을 추출하여 SecurityContext에 인증 정보를 주입한다.
 * - VALID  → CustomUserDetails를 principal로 SecurityContext 설정
 * - EXPIRED → request attribute "jwt.error"="EXPIRED" 저장 (JwtAuthEntryPoint에서 A003 응답)
 * - INVALID → request attribute "jwt.error"="INVALID" 저장 (JwtAuthEntryPoint에서 A002 응답)
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            JwtUtil.TokenStatus status = jwtUtil.validateToken(token);

            if (status == JwtUtil.TokenStatus.VALID) {
                // CustomUserDetails로 principal 구성 (userId + email + role)
                CustomUserDetails userDetails = new CustomUserDetails(
                        jwtUtil.getUserId(token),
                        jwtUtil.getEmail(token),
                        jwtUtil.getRole(token)
                );
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities() // CustomUserDetails에서 권한 위임
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("[JwtAuthFilter] 인증 성공: userId={}, role={}",
                        userDetails.getUserId(), userDetails.getRole());

            } else if (status == JwtUtil.TokenStatus.EXPIRED) {
                // EntryPoint에서 EXPIRED_TOKEN(A003) 응답할 수 있도록 전달
                request.setAttribute("jwt.error", "EXPIRED");

            } else {
                // EntryPoint에서 INVALID_TOKEN(A002) 응답할 수 있도록 전달
                request.setAttribute("jwt.error", "INVALID");
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더에서 Bearer 토큰 추출
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * /api/auth/** 경로는 필터 스킵 (회원가입·로그인은 토큰 없이 접근)
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/");
    }
}
