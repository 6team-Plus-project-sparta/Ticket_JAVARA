package com.example.ticket_javara.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== AUTH (A) =====
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "A001", "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A003", "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A004", "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "A005", "유효하지 않은 토큰입니다."),

    // ===== USER (U) =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "존재하지 않는 사용자입니다."),
    USER_FORBIDDEN(HttpStatus.FORBIDDEN, "U002", "본인의 정보만 수정할 수 있습니다."),

    // ===== EVENT (E) =====
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "존재하지 않는 이벤트입니다."),
    EVENT_FORBIDDEN(HttpStatus.FORBIDDEN, "E002", "이벤트 관리 권한이 없습니다."),
    INVALID_SALE_DATE(HttpStatus.BAD_REQUEST, "E003",
        "예매 오픈/마감 시각이 올바르지 않습니다. (saleStartAt < saleEndAt < eventDate)"),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "E004", "존재하지 않는 구역입니다."),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "E005", "존재하지 않는 좌석입니다."),

    // ===== HOLD / SEAT (S) =====
    HOLD_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "S001", "최대 4석까지만 선택할 수 있습니다."),
    SEAT_LOCK_FAILED(HttpStatus.CONFLICT, "S002", "다른 사용자가 처리 중입니다. 다른 좌석을 선택해주세요."),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "S003", "이미 선점된 좌석입니다."),
    SEAT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "S004", "이미 예매 확정된 좌석입니다."),
    HOLD_NOT_OWNED(HttpStatus.FORBIDDEN, "S005", "본인이 선점한 좌석만 해제할 수 있습니다."),
    HOLD_NOT_FOUND(HttpStatus.NOT_FOUND, "S006", "점유 정보가 없거나 이미 만료되었습니다."),

    // ===== ORDER / BOOKING (O) =====
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "존재하지 않는 주문입니다."),
    ORDER_NOT_OWNED(HttpStatus.FORBIDDEN, "O002", "본인의 주문만 조회할 수 있습니다."),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "O003", "점유 시간이 만료된 좌석이 있습니다. 좌석을 다시 선택해주세요."),
    ORDER_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "O004", "이미 취소된 주문입니다."),
    CANCEL_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "O005",
        "취소 가능 기간이 지났습니다. (이벤트 시작 24시간 이전까지만 취소 가능)"),
    CANCEL_NOT_OWNED(HttpStatus.FORBIDDEN, "O006", "본인의 주문만 취소할 수 있습니다."),
    BOOKING_NOT_PENDING(HttpStatus.BAD_REQUEST, "O007",
        "PENDING 상태의 예매만 수동 확정할 수 있습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "O008", "결제 기록이 확인되지 않습니다."),

    // ===== COUPON (C) =====
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "C001", "존재하지 않는 쿠폰입니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "C002", "이미 발급받은 쿠폰입니다."),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT, "C003", "쿠폰이 모두 소진되었습니다."),
    COUPON_NOT_STARTED(HttpStatus.BAD_REQUEST, "C004", "쿠폰 발급 시간이 아닙니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "C005", "만료된 쿠폰입니다."),
    COUPON_INVALID(HttpStatus.BAD_REQUEST, "C006", "사용할 수 없는 쿠폰입니다."),
    COUPON_NOT_OWNED(HttpStatus.FORBIDDEN, "C007", "본인 소유의 쿠폰만 사용할 수 있습니다."),
    COUPON_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "C008",
        "현재 서비스 이용이 어렵습니다. 잠시 후 다시 시도해주세요."),

    // ===== CHAT (H) =====
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "H001", "존재하지 않는 채팅방입니다."),
    CHAT_ROOM_FORBIDDEN(HttpStatus.FORBIDDEN, "H002", "해당 채팅방에 대한 권한이 없습니다."),
    CHAT_ROOM_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "H003", "이미 종료된 채팅방입니다."),

    // ===== GLOBAL/COMMON (G) =====
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "G001", "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "G002", "요청한 리소스를 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "G003", "접근 권한이 없습니다."),
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "G004", "관리자 권한이 필요합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G005", "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
