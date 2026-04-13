package com.example.ticket_javara.global.exception;

/** 404 Not Found 계열 예외 */
public class NotFoundException extends BusinessException {
    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
