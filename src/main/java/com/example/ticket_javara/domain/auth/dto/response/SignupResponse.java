package com.example.ticket_javara.domain.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 회원가입 응답 DTO
 */
@Getter
@Builder
public class SignupResponse {
    private Long userId;
    private String email;
    private String nickname;
    private LocalDateTime createdAt;
}
