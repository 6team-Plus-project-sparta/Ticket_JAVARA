package com.example.ticket_javara.domain.search.dto.response;

import com.example.ticket_javara.domain.event.entity.EventCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class EventSummaryResponseDto {
    private Long eventId;
    private String title;
    private EventCategory category;
    private String venueName;
    private LocalDateTime eventDate;
    private Integer minPrice;
    private Long remainingSeats;
    private String thumbnailUrl;
}
