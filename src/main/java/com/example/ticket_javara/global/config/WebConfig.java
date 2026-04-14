package com.example.ticket_javara.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 설정 (인터셉터 등록 등)
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final WebhookSignatureInterceptor webhookSignatureInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 웹훅 엔드포인트에 대해서만 서명 검증 수행
        registry.addInterceptor(webhookSignatureInterceptor)
                .addPathPatterns("/api/mock-pg/webhook");
    }
}
