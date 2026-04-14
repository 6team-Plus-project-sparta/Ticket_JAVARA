package com.example.ticket_javara.domain.event.controller;

import com.example.ticket_javara.domain.event.dto.response.EventDetailResponseDto;
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
}
