package com.example.ticket_javara.global.common;

import com.example.ticket_javara.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String code;
    private final String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime timestamp;
    private List<FieldErrorDetail> errors;

    private ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    /** 단순 비즈니스 에러 */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }

    /** 동적 메시지 오버라이드 */
    public static ErrorResponse of(ErrorCode errorCode, String detailMessage) {
        return new ErrorResponse(errorCode.getCode(), detailMessage);
    }

    /** @Valid 유효성 실패 — 필드별 에러 목록 포함 */
    public static ErrorResponse ofValidation(List<FieldError> fieldErrors) {
        ErrorResponse response = new ErrorResponse(
            ErrorCode.INVALID_REQUEST.getCode(),
            ErrorCode.INVALID_REQUEST.getMessage()
        );
        response.errors = fieldErrors.stream()
            .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return response;
    }

    @Getter
    @AllArgsConstructor
    public static class FieldErrorDetail {
        private final String field;
        private final String message;
    }
}
