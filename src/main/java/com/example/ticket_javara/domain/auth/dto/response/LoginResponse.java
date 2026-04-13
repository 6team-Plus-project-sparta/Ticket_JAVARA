package com.example.ticket_javara.domain.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 응답 DTO
 */
@Getter
@Builder
public class LoginResponse {
    private String accessToken;
    private int expiresIn;   // 초 단위
    private String tokenType;
}
