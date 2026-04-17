package com.example.ticket_javara.domain.event.dto.response;

import com.example.ticket_javara.domain.event.entity.EventCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDetailResponseDto implements Serializable {
    private Long eventId;
    private String title;
    private EventCategory category;
    private VenueDto venue;
    private LocalDateTime eventDate;
    private String description;
    private String thumbnailUrl;
    private List<SectionDetailDto> sections;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VenueDto {
        private Long venueId;
        private String name;
        private String address;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionDetailDto {
        private Long sectionId;
        private String sectionName;
        private Integer price;
        private Integer totalSeats;
        private Long remainingSeats;
    }
}
