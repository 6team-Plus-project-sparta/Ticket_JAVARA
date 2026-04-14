package com.example.ticket_javara.global.exception;

import com.example.ticket_javara.global.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 1. 모든 BusinessException (NotFoundException, ForbiddenException 등) ────
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec, e.getMessage()));
    }

    // ── 2. @Valid / @Validated 유효성 실패 ──────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.ofValidation(e.getBindingResult().getFieldErrors()));
    }

    // ── 3. @RequestParam / @PathVariable 타입 불일치 ────────────────────────────
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST,
                String.format("'%s' 파라미터 값이 올바르지 않습니다.", e.getName())));
    }

    // ── 4. 지원하지 않는 HTTP 메서드 ────────────────────────────────────────────
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST,
                String.format("'%s' 메서드는 지원하지 않습니다.", e.getMethod())));
    }

    // ── 5. DB unique constraint 위반 ────────────────────────────────────────────
    //      (USER_COUPON UNIQUE(user_id, coupon_id), ACTIVE_BOOKING PK 중복 등)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation: {}", e.getMessage());

        String message = e.getMostSpecificCause() != null
            ? e.getMostSpecificCause().getMessage() : e.getMessage();

        // USER_COUPON UNIQUE 위반 → C002로 매핑
        if (message != null && message.contains("uk_user_coupon")) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.COUPON_ALREADY_ISSUED));
        }

        // ACTIVE_BOOKING PK 위반 (좌석 중복 확정 최후 방어선)
        if (message != null && message.contains("active_booking")) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.SEAT_ALREADY_CONFIRMED));
        }

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, "중복되거나 이미 처리된 요청입니다."));
    }

    // ── 6. 미처리 예외 폴백 (시스템 에러) ───────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e,
                                                          HttpServletRequest request) {
        log.error("[INTERNAL_ERROR] {} {}: {}",
            request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
