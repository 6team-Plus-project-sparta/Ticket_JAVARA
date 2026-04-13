package com.example.ticket_javara.global.exception;

import lombok.Getter;

/**
 * 비즈니스 예외 기본 클래스
 * 모든 도메인 예외는 이 클래스를 상속하거나 직접 사용한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
