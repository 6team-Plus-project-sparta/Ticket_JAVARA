package com.example.ticket_javara.domain.search.dto.response;

import com.example.ticket_javara.domain.event.entity.EventCategory;
import com.example.ticket_javara.domain.event.entity.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventSummaryResponseDto {
    private Long eventId;
    private String title;
    private EventCategory category;
    private String venueName;
    private LocalDateTime eventDate;
    private Integer minPrice;
    private Long remainingSeats;
    private String thumbnailUrl;
    private EventStatus eventStatus;

    /**
     * remainingSeats 업데이트 전용 메서드
     * Service 계층에서 일괄 업데이트 시 사용
     */
    public void updateRemainingSeats(Long remainingSeats) {
        this.remainingSeats = remainingSeats;
    }
}
