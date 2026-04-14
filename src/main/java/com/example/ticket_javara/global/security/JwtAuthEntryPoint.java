package com.example.ticket_javara.global.security;

import com.example.ticket_javara.global.common.ErrorResponse;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증 실패 시 401 Unauthorized 응답 처리
 * JwtAuthFilter에서 저장한 "jwt.error" request attribute로 만료(A003) / 무효(A002)를 구분한다.
 *
 * - jwt.error = "EXPIRED" → EXPIRED_TOKEN (A003) 인증이 만료되었습니다.
 * - jwt.error = "INVALID" → INVALID_TOKEN  (A002) 유효하지 않은 토큰입니다.
 * - jwt.error = null      → INVALID_TOKEN  (A002) 토큰 없이 인증 필요 API 접근
 */
@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // 필터에서 저장한 jwt.error 속성으로 만료 vs 무효 구분
        String jwtError = (String) request.getAttribute("jwt.error");
        ErrorCode errorCode = "EXPIRED".equals(jwtError)
                ? ErrorCode.TOKEN_EXPIRED   // A004 — 토큰이 만료되었습니다.
                : ErrorCode.TOKEN_INVALID;  // A005 — 유효하지 않은 토큰입니다.

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
