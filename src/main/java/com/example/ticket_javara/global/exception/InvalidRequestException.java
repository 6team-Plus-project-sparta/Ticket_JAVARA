package com.example.ticket_javara.global.exception;

/** 400 Bad Request 계열 예외 */
public class InvalidRequestException extends BusinessException {
    public InvalidRequestException(ErrorCode errorCode) {
        super(errorCode);
    }
}
