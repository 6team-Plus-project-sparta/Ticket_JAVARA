package com.example.ticket_javara.domain.event.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SeatResponseDto {

    private Long eventId;
    private List<SectionSeatDto> sections;

    @Getter
    @Builder
    public static class SectionSeatDto {
        private Long sectionId;
        private String sectionName;
        private int price;
        private List<SeatDto> seats;
    }

    @Getter
    @Builder
    public static class SeatDto {
        private Long seatId;
        private String rowName;
        private int colNum;
        private String seatNumber;  // "A-1" 형태
        private SeatStatus status;
    }

    public enum SeatStatus {
        AVAILABLE, ON_HOLD, CONFIRMED
    }
}

