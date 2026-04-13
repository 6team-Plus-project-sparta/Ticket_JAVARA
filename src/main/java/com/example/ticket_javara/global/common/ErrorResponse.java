package com.example.ticket_javara.global.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 공통 에러 응답 형식
 * { code, message, timestamp }
 * 참고: 12_공통_에러코드_설계서.md
 */
@Getter
public class ErrorResponse {

    private final String code;
    private final String message;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    private ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message);
    }
}
