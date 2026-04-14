package com.example.ticket_javara.domain.event.dto.response;

import com.example.ticket_javara.domain.event.entity.EventCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EventSummaryResponseDto {
    private Long eventId;
    private String title;
    private EventCategory category;
    private String venueName;
    private LocalDateTime eventDate;
    private int minPrice;
    private long remainingSeats;
    private String thumbnailUrl;
}
