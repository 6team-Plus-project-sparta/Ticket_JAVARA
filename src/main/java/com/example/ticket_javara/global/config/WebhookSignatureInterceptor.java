package com.example.ticket_javara.global.config;

import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * PG 웹훅 호출자 검증(무결성 및 인증) 인테셉터
 */
@Slf4j
@Component
public class WebhookSignatureInterceptor implements HandlerInterceptor {

    // Mock PG용 시크릿 키 하드코딩 (실제 운영에서는 환경변수나 프로퍼티 사용)
    private static final String MOCK_PG_SECRET = "mock-secret-signature";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String signature = request.getHeader("x-webhook-signature");
        
        if (signature == null || !signature.equals(MOCK_PG_SECRET)) {
            log.error("[WebhookSignatureInterceptor] 올바르지 않은 웹훅 서명입니다. signature={}", signature);
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        
        return true;
    }
}
