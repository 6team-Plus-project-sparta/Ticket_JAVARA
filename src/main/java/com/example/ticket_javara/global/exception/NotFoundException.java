package com.example.ticket_javara.global.exception;

/** 404 Not Found 계열 예외 */
public class NotFoundException extends BusinessException {
    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
    // 이것도 함께 추가해 주세요
    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
