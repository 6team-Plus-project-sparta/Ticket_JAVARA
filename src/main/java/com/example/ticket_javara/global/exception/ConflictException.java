package com.example.ticket_javara.global.exception;

/** 409 Conflict 계열 예외 */
public class ConflictException extends BusinessException {
    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }
}
