package com.example.ticket_javara.domain.event.controller;

import com.example.ticket_javara.domain.event.dto.request.EventCreateRequestDto;
import com.example.ticket_javara.domain.event.dto.request.EventStatusUpdateRequestDto;
import com.example.ticket_javara.domain.event.dto.response.EventCreateResponseDto;
import com.example.ticket_javara.domain.event.dto.response.EventStatusUpdateResponseDto;
import com.example.ticket_javara.domain.event.service.EventScheduler;
import com.example.ticket_javara.domain.event.service.EventService;
import com.example.ticket_javara.global.common.ApiResponse;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import com.example.ticket_javara.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class EventAdminController {

    private final EventService eventService;
    private final EventScheduler eventScheduler;//스케쥴러테스트용

    @PostMapping
    public ResponseEntity<ApiResponse<EventCreateResponseDto>> createEvent(
            @Valid @RequestBody EventCreateRequestDto requestDto) {
        
        String role = SecurityUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }

        Long adminId = SecurityUtil.getCurrentUserId();
        EventCreateResponseDto responseDto = eventService.createEvent(requestDto, adminId);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(responseDto, "201", "Created"));
    }

    //이벤트 상태변경 엔드포인트
    @PatchMapping("/{eventId}/status")
    public ResponseEntity<ApiResponse<EventStatusUpdateResponseDto>> updateEventStatus(
            @PathVariable Long eventId,
            @Valid @RequestBody EventStatusUpdateRequestDto requestDto) {

        // 기존 createEvent와 동일한 ADMIN 권한 체크 패턴
        String role = SecurityUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException(ErrorCode.ADMIN_ONLY);
        }

        EventStatusUpdateResponseDto responseDto =
                eventService.updateEventStatus(eventId, requestDto);

        return ResponseEntity.ok(ApiResponse.of(responseDto, "200", "OK"));
    }

    // 임시 추가 스케쥴러 테스트용
    @PostMapping("/scheduler/end-events")
    public ResponseEntity<String> triggerEndEvents() {
        eventScheduler.updateEndedEvents();
        return ResponseEntity.ok("ENDED 전환 완료");
    }
}
