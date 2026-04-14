package com.example.ticket_javara.global.common;

import lombok.Getter;

/**
 * 공통 API 응답 래퍼
 * 성공: { data, code: "200", message: "OK" }
 * 참고: 12_공통_에러코드_설계서.md §8
 */
@Getter
public class ApiResponse<T> {

    private final T data;
    private final String code;
    private final String message;

    private ApiResponse(T data, String code, String message) {
        this.data = data;
        this.code = code;
        this.message = message;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, "200", "OK");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(data, "200", message);
    }

    public static <T> ApiResponse<T> of(T data, String code, String message) {
        return new ApiResponse<>(data, code, message);
    }
}
