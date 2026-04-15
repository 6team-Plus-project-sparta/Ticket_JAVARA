package com.example.ticket_javara.global.config;

import com.example.ticket_javara.global.security.JwtAccessDeniedHandler;
import com.example.ticket_javara.global.security.JwtAuthEntryPoint;
import com.example.ticket_javara.global.security.JwtAuthFilter;
import com.example.ticket_javara.global.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

/**
 * Spring Security 설정
 * - Stateless (JWT 기반, 세션 미사용)
 * - CSRF 비활성화 (REST API)
 * - 공개 API / 인증 필요 API / ADMIN 전용 API 구분
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtUtil);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS 설정 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // REST API — CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless 세션 (JWT 사용)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 예외 핸들러 등록
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))

                // 요청별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 🔓 공개 API (인증 불필요)
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/search", "/api/v2/events/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/search/popular").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/mock-pg/webhook").permitAll()
                        // Health Check
                        .requestMatchers("/actuator/health").permitAll()
                        // WebSocket
                        .requestMatchers("/ws-stomp/**").permitAll()

                        // 🔐👑 ADMIN 전용 API
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 🔐 그 외 모든 API는 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 필터 등록 (UsernamePasswordAuthenticationFilter 앞에)
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 정책 화이트리스트 수동 셋팅
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. 허용할 출처(Origin) 설정 - yml에서 가져온 화이트리스트 주입
        configuration.setAllowedOrigins(allowedOrigins);

        // 2. 허용할 HTTP 메서드 등록 (Preflight OPTIONS 필수 포함)
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 3. 허용할 헤더 (Authorization을 보내야 하므로 필수)
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));

        // 4. 노출할 응답 헤더 (프론트가 응답에서 Authorization 토큰을 읽어야 할 경우)
        configuration.setExposedHeaders(List.of("Authorization"));

        // 5. Credentials 허용 (Header 방식이지만 범용성을 위해 true 처리, '*' 와일드카드 원천 금지)
        configuration.setAllowCredentials(true);

        // 모든 경로("/**")에 대해 위 CORS 정책 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
