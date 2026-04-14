package com.example.ticket_javara.global.exception;

/** 403 Forbidden 계열 예외 */
public class ForbiddenException extends BusinessException {
    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }
}
