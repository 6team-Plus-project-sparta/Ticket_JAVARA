package com.example.ticket_javara.domain.search.service;

import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.repository.SeatRepository;
import com.example.ticket_javara.domain.search.dto.request.SearchRequestDto;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.example.ticket_javara.domain.search.repository.EventSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final EventSearchRepository eventSearchRepository;
    private final SeatRepository seatRepository;

    public Page<EventSummaryResponseDto> searchEventsV1(SearchRequestDto requestDto, Pageable pageable) {
        long startTime = System.currentTimeMillis();

        Page<Event> events = eventSearchRepository.searchEvents(requestDto, pageable);

        Page<EventSummaryResponseDto> result = events.map(event -> {
            Integer minPrice = event.getSections().stream()
                    .mapToInt(section -> section.getPrice())
                    .min()
                    .orElse(0);

            long remainingSeats = seatRepository.countAvailableSeatsByEventId(event.getEventId());

            return EventSummaryResponseDto.builder()
                    .eventId(event.getEventId())
                    .title(event.getTitle())
                    .category(event.getCategory())
                    .venueName(event.getVenue().getName())
                    .eventDate(event.getEventDate())
                    .minPrice(minPrice)
                    .remainingSeats(remainingSeats)
                    .thumbnailUrl(event.getThumbnailUrl())
                    .build();
        });

        long endTime = System.currentTimeMillis();
        log.info("[V1 Search] query execution time: {} ms", (endTime - startTime));

        return result;
    }
}
