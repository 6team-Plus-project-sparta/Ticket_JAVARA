package com.example.ticket_javara.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {


    // ──────────────────────────────────────────────────
    // A: 인증 (Auth)
    // ──────────────────────────────────────────────────
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "A001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "토큰이 만료되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A004", "접근 권한이 없습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.UNAUTHORIZED, "A005", "현재 비밀번호가 올바르지 않습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "A006", "이미 가입된 이메일입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A007", "인증이 필요합니다."),

    // ──────────────────────────────────────────────────
    // U: 사용자 (User)
    // ──────────────────────────────────────────────────
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "U001", "이미 가입된 이메일입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "U002", "이미 사용 중인 닉네임입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U003", "존재하지 않는 사용자입니다."),
    USER_FORBIDDEN(HttpStatus.FORBIDDEN, "U004", "본인의 정보만 수정할 수 있습니다."),

    // ──────────────────────────────────────────────────
    // E: 이벤트 (Event)
    // ──────────────────────────────────────────────────
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "존재하지 않는 이벤트입니다."),
    INVALID_EVENT_DATE(HttpStatus.BAD_REQUEST, "E002", "이벤트 일시는 현재 이후여야 합니다."),
    INVALID_SALE_DATE(HttpStatus.BAD_REQUEST, "E003", "예매 오픈/마감 시각이 올바르지 않습니다. (saleStartAt < saleEndAt < eventDate)"),
    VENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "E004", "존재하지 않는 공연장입니다."),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "E005", "존재하지 않는 구역입니다."),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "E006", "존재하지 않는 좌석입니다."),
    EVENT_FORBIDDEN(HttpStatus.FORBIDDEN, "E007", "이벤트 관리 권한이 없습니다."),

    // ──────────────────────────────────────────────────
    // S: 좌석/Hold (Seat)
    // ──────────────────────────────────────────────────
    HOLD_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "S001", "최대 4석까지만 선택할 수 있습니다."),
    SEAT_LOCK_FAILED(HttpStatus.CONFLICT, "S002", "다른 사용자가 처리 중입니다. 다른 좌석을 선택해주세요."),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "S003", "이미 선점된 좌석입니다."),
    SEAT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "S004", "이미 예매된 좌석입니다."),
    HOLD_NOT_FOUND(HttpStatus.NOT_FOUND, "S005", "점유 정보가 없거나 이미 만료되었습니다."),
    HOLD_NOT_OWNED(HttpStatus.FORBIDDEN, "S006", "본인이 선점한 좌석만 해제할 수 있습니다."),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "S007", "점유 시간이 만료된 좌석이 있습니다. 좌석을 다시 선택해주세요."),

    // ──────────────────────────────────────────────────
    // O: 주문/예매 (Order/Booking)
    // ──────────────────────────────────────────────────
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "존재하지 않는 주문입니다."),
    ORDER_NOT_OWNED(HttpStatus.FORBIDDEN, "O002", "본인의 주문만 조회할 수 있습니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "O003", "이미 취소된 주문입니다."),
    CANCEL_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "O004", "취소 가능 기간이 지났습니다. (이벤트 시작 24시간 전까지)"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "O005", "존재하지 않는 예매입니다."),
    BOOKING_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "O006", "이미 해당 좌석이 다른 예매로 확정되어 있습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "O007", "결제 기록이 확인되지 않습니다. 확정을 중단합니다."),
    HOLDING_EXPIRED(HttpStatus.CONFLICT, "O008", "점유 시간이 만료된 좌석이 있습니다. 좌석을 다시 선택해주세요."),
    CANCEL_NOT_OWNED(HttpStatus.FORBIDDEN, "O009", "본인의 주문만 취소할 수 있습니다."),
    BOOKING_NOT_PENDING(HttpStatus.BAD_REQUEST, "O010","PENDING 상태의 예매만 수동 확정할 수 있습니다."),


    // ──────────────────────────────────────────────────
    // C: 쿠폰 (Coupon)
    // ──────────────────────────────────────────────────
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "C001", "존재하지 않는 쿠폰입니다."),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT, "C002", "쿠폰이 모두 소진되었습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "C003", "이미 발급받은 쿠폰입니다."),
    COUPON_INVALID(HttpStatus.BAD_REQUEST, "C004", "사용할 수 없는 쿠폰입니다."),
    COUPON_NOT_OWNED(HttpStatus.BAD_REQUEST, "C005", "본인 소유의 쿠폰만 사용할 수 있습니다."),
    COUPON_NOT_STARTED(HttpStatus.BAD_REQUEST, "C006", "아직 발급 시작 전인 쿠폰입니다."),
    COUPON_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "C007", "현재 서비스 이용이 어렵습니다. 잠시 후 다시 시도해주세요."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "C008", "만료된 쿠폰입니다."),

    // ──────────────────────────────────────────────────
    // H: 채팅 (Chat)
    // ──────────────────────────────────────────────────
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "H001", "존재하지 않는 채팅방입니다."),
    CHAT_ROOM_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "H002", "이미 종료된 채팅방입니다."),
    CHAT_UNAUTHORIZED(HttpStatus.FORBIDDEN, "H003", "해당 채팅방에 접근 권한이 없습니다."),

    // ──────────────────────────────────────────────────
    // G: 공통 (Global)
    // ──────────────────────────────────────────────────
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "G001", "입력값 유효성 검증에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G002", "서버 내부 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "G003", "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "G004", "요청한 리소스를 찾을 수 없습니다."),
    ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "G005", "접근 권한이 없습니다."),
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "G006", "관리자 권한이 필요합니다.")
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
