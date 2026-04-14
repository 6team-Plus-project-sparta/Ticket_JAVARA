package com.example.ticket_javara.domain.booking.controller;

import com.example.ticket_javara.domain.booking.dto.response.HoldResponseDto;
import com.example.ticket_javara.domain.booking.facade.HoldLockFacade;
import com.example.ticket_javara.domain.booking.service.HoldService;
import com.example.ticket_javara.global.common.ApiResponse;
import com.example.ticket_javara.global.security.CustomUserDetails;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 좌석 Hold 컨트롤러
 * API:
 *   POST   /api/events/{eventId}/seats/{seatId}/hold  — Hold 획득 ⚠️ 동시성
 *   DELETE /api/events/{eventId}/seats/{seatId}/hold  — Hold 수동 해제
 */
@Slf4j
@RestController
@RequestMapping("/api/events/{eventId}/seats/{seatId}/hold")
@RequiredArgsConstructor
@Validated
public class HoldController {

    private final HoldLockFacade holdLockFacade;
    private final HoldService holdService;

    /**
     * POST /api/events/{eventId}/seats/{seatId}/hold
     * 좌석 임시 점유 (FN-SEAT-02)
     *
     * 동시성 제어: Lettuce SETNX (HoldLockFacade에서 처리)
     * Fail Fast: 락 획득 실패 시 즉시 409 SEAT_LOCK_FAILED
     *
     * @return 200 OK { holdToken, seatId, expiresAt }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<HoldResponseDto>> holdSeat(
            @PathVariable @Positive(message = "eventId는 양수여야 합니다.") Long eventId,
            @PathVariable @Positive(message = "seatId는 양수여야 합니다.") Long seatId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        log.info("[HoldController] Hold 요청 eventId={}, seatId={}, userId={}", eventId, seatId, userId);

        // HoldLockFacade: 분산락 획득 → HoldService.processHold() → 락 해제
        HoldResponseDto response = holdLockFacade.holdSeat(eventId, seatId, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * DELETE /api/events/{eventId}/seats/{seatId}/hold
     * Hold 수동 해제 (FN-SEAT-03)
     *
     * @return 200 OK
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> releaseHold(
            @PathVariable @Positive(message = "eventId는 양수여야 합니다.") Long eventId,
            @PathVariable @Positive(message = "seatId는 양수여야 합니다.") Long seatId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        log.info("[HoldController] Hold 해제 요청 eventId={}, seatId={}, userId={}", eventId, seatId, userId);

        holdService.releaseHold(eventId, seatId, userId);

        return ResponseEntity.ok(ApiResponse.success(null, "좌석 선택이 해제되었습니다."));
    }
}
