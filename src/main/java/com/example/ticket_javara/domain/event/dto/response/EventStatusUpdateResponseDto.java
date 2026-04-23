package com.example.ticket_javara.domain.event.dto.response;

import com.example.ticket_javara.domain.event.entity.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStatusUpdateResponseDto {
    private Long eventId;
    private EventStatus status;

    public static EventStatusUpdateResponseDto of(Long eventId, EventStatus status) {
        return EventStatusUpdateResponseDto.builder()
                .eventId(eventId)
                .status(status)
                .build();
    }
}
