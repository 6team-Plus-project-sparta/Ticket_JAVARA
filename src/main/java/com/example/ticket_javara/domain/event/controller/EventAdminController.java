package com.example.ticket_javara.domain.event.controller;

import com.example.ticket_javara.domain.event.dto.request.EventCreateRequestDto;
import com.example.ticket_javara.domain.event.dto.response.EventCreateResponseDto;
import com.example.ticket_javara.domain.event.service.EventService;
import com.example.ticket_javara.global.common.ApiResponse;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.ForbiddenException;
import com.example.ticket_javara.global.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class EventAdminController {

    private final EventService eventService;

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
}
