package com.example.ticket_javara.domain.auth.controller;

import com.example.ticket_javara.domain.auth.dto.request.LoginRequest;
import com.example.ticket_javara.domain.auth.dto.request.SignupRequest;
import com.example.ticket_javara.domain.auth.dto.response.LoginResponse;
import com.example.ticket_javara.domain.auth.dto.response.SignupResponse;
import com.example.ticket_javara.domain.auth.service.AuthService;
import com.example.ticket_javara.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 컨트롤러 (UC-001, UC-002)
 * POST /api/auth/signup  — 회원가입
 * POST /api/auth/login   — 로그인 (JWT 발급)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 🔓 회원가입 */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /** 🔓 로그인 */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        for (int i = 0; i < 100; i++) {
            System.out.println(i);
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
