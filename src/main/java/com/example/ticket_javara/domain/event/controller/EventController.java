package com.example.ticket_javara.domain.event.controller;

import com.example.ticket_javara.domain.event.dto.response.EventDetailResponseDto;
import com.example.ticket_javara.domain.event.dto.response.SeatResponseDto;
import com.example.ticket_javara.domain.event.entity.EventCategory;
import com.example.ticket_javara.domain.event.entity.EventStatus;
import com.example.ticket_javara.domain.event.service.EventService;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.example.ticket_javara.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventDetailResponseDto>> getEventDetail(@PathVariable("eventId") Long eventId) {
        EventDetailResponseDto responseDto = eventService.getEventDetail(eventId);
        return ResponseEntity.ok(ApiResponse.success(responseDto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EventSummaryResponseDto>>> getEventList(
            @RequestParam(required = false) EventCategory category,
            @RequestParam(required = false) EventStatus status,
            @PageableDefault(size = 20, sort = "eventDate", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<EventSummaryResponseDto> result = eventService.getEventList(category, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 이벤트 ID로 좌석 정보 조회
     * GET /api/events/{eventId}/seats
     * GET /api/events/{eventId}/seats?sectionId=10  (구역 단위 필터)
     *
     * 🔐 인증 필요 (SA 문서 UC-007 기준 — 좌석 선택은 로그인 사용자만)
     */
    @GetMapping("/{eventId}/seats")
    public ResponseEntity<ApiResponse<SeatResponseDto>> getSeatsByEvent(
            @PathVariable("eventId") Long eventId,
            @RequestParam(required = false) Long sectionId) {

        SeatResponseDto responseDto = eventService.getSeatsByEventId(eventId, sectionId);
        return ResponseEntity.ok(ApiResponse.success(responseDto));
    }
}
