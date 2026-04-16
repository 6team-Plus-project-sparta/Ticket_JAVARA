package com.example.ticket_javara.domain.event.service;

import com.example.ticket_javara.domain.event.dto.request.EventCreateRequestDto;
import com.example.ticket_javara.domain.event.dto.request.SectionCreateDto;
import com.example.ticket_javara.domain.event.dto.response.EventCreateResponseDto;
import com.example.ticket_javara.domain.event.dto.response.EventDetailResponseDto;
import com.example.ticket_javara.domain.event.entity.*;
import com.example.ticket_javara.domain.event.repository.EventRepository;
import com.example.ticket_javara.domain.event.repository.SeatRepository;
import com.example.ticket_javara.domain.event.repository.SectionRepository;
import com.example.ticket_javara.domain.event.repository.VenueRepository;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.InvalidRequestException;
import com.example.ticket_javara.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.example.ticket_javara.global.event.EventCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final SectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public EventCreateResponseDto createEvent(EventCreateRequestDto requestDto, Long adminId) {
        if (!requestDto.getSaleStartAt().isBefore(requestDto.getSaleEndAt()) ||
                !requestDto.getSaleEndAt().isBefore(requestDto.getEventDate())) {
            throw new InvalidRequestException(ErrorCode.INVALID_SALE_DATE);
        }

        if (requestDto.getEventDate().isBefore(LocalDateTime.now())) {
            throw new InvalidRequestException(ErrorCode.INVALID_REQUEST, "이벤트 일시는 현재 이후여야 합니다.");
        }

        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));

        Venue venue = venueRepository.findById(requestDto.getVenueId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "공연장을 찾을 수 없습니다."));

        Event event = Event.builder()
                .venue(venue)
                .createdBy(adminUser)
                .title(requestDto.getTitle())
                .category(requestDto.getCategory())
                .eventDate(requestDto.getEventDate())
                .saleStartAt(requestDto.getSaleStartAt())
                .saleEndAt(requestDto.getSaleEndAt())
                .roundNumber(requestDto.getRoundNumber())
                .status(EventStatus.ON_SALE)
                .description(requestDto.getDescription())
                .thumbnailUrl(requestDto.getThumbnailUrl())
                .build();

        event = eventRepository.save(event);

        int totalSeats = 0;
        int sectionsCreated = 0;

        for (SectionCreateDto sDto : requestDto.getSections()) {
            int currentSectionTotalSeats = sDto.getRowCount() * sDto.getColCount();

            Section section = Section.builder()
                    .event(event)
                    .sectionName(sDto.getSectionName())
                    .price(sDto.getPrice())
                    .totalSeats(currentSectionTotalSeats)
                    .build();

            section = sectionRepository.save(section);

            List<Seat> seatsToSave = new ArrayList<>();
            for (int i = 0; i < sDto.getRowCount(); i++) {
                String rowName = String.valueOf((char) ('A' + i));
                for (int j = 1; j <= sDto.getColCount(); j++) {
                    Seat seat = Seat.builder()
                            .section(section)
                            .rowName(rowName)
                            .colNum(j)
                            .build();
                    seatsToSave.add(seat);
                }
            }
            seatRepository.saveAll(seatsToSave);

            totalSeats += currentSectionTotalSeats;
            sectionsCreated++;
        }

        eventPublisher.publishEvent(new EventCreatedEvent());
        log.info("[EventService] EventCreatedEvent 발행 — 검색 캐시 무효화 트리거");

        return EventCreateResponseDto.builder()
                .eventId(event.getEventId())
                .title(event.getTitle())
                .totalSeats(totalSeats)
                .sectionsCreated(sectionsCreated)
                .build();
    }

    public EventDetailResponseDto getEventDetail(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EVENT_NOT_FOUND));

        List<EventDetailResponseDto.SectionDetailDto> sectionDtos = new ArrayList<>();

        for (Section section : event.getSections()) {
            long remainingSeats = seatRepository.countAvailableSeatsBySectionId(section.getSectionId());

            sectionDtos.add(EventDetailResponseDto.SectionDetailDto.builder()
                    .sectionId(section.getSectionId())
                    .sectionName(section.getSectionName())
                    .price(section.getPrice())
                    .totalSeats(section.getTotalSeats())
                    .remainingSeats(remainingSeats)
                    .build());
        }

        return EventDetailResponseDto.builder()
                .eventId(event.getEventId())
                .title(event.getTitle())
                .category(event.getCategory())
                .venue(EventDetailResponseDto.VenueDto.builder()
                        .venueId(event.getVenue().getVenueId())
                        .name(event.getVenue().getName())
                        .address(event.getVenue().getAddress())
                        .build())
                .eventDate(event.getEventDate())
                .description(event.getDescription())
                .thumbnailUrl(event.getThumbnailUrl())
                .sections(sectionDtos)
                .build();
    }

    public Page<EventSummaryResponseDto> getEventList(EventCategory category, EventStatus status, Pageable pageable) {
        Page<Event> events;

        if (category != null && status != null) {
            events = eventRepository.findByCategoryAndStatus(category, status, pageable);
        } else if (category != null) {
            events = eventRepository.findByCategory(category, pageable);
        } else if (status != null) {
            events = eventRepository.findByStatus(status, pageable);
        } else {
            events = eventRepository.findAll(pageable);
        }

        return events.map(event -> {
            // 최저가 계산
            int minPrice = event.getSections().stream()
                    .mapToInt(Section::getPrice)
                    .min()
                    .orElse(0);

            // 잔여좌석 계산
            long remainingSeats = event.getSections().stream()
                    .mapToLong(section -> seatRepository.countAvailableSeatsBySectionId(section.getSectionId()))
                    .sum();

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
    }
}
