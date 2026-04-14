package com.example.ticket_javara.domain.event.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCreateResponseDto {
    private Long eventId;
    private String title;
    private Integer totalSeats;
    private Integer sectionsCreated;
}
