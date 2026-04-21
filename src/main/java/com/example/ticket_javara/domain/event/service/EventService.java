package com.example.ticket_javara.domain.event.service;

import com.example.ticket_javara.domain.event.dto.request.EventCreateRequestDto;
import com.example.ticket_javara.domain.event.dto.request.SectionCreateDto;
import com.example.ticket_javara.domain.event.dto.response.EventCreateResponseDto;
import com.example.ticket_javara.domain.event.dto.response.EventDetailResponseDto;
import com.example.ticket_javara.domain.event.dto.response.SeatResponseDto;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final RedisTemplate<String, String> redisTemplate;
    private final EventDetailCacheService eventDetailCacheService; // BUG-01: 캐시 전용 Bean 분리

    @Transactional
    @CacheEvict(value = "event-detail", allEntries = true)
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

    /**
     * 이벤트 상세 조회 (BUG-01 수정)
     *
     * ① 이벤트 기본 정보: EventDetailCacheService.getCachedEventDetail() — TTL 10분 캐시 활용
     * ② 잔여 좌석 수   : 캐시 밖에서 실시간 조회 후 새 SectionDetailDto로 교체
     *
     * self-invocation 방지: @Cacheable이 붙은 메서드를 별도 Bean(EventDetailCacheService)에 위임
     */
    public EventDetailResponseDto getEventDetail(Long eventId) {
        // ① 기본 정보 캐시 조회 (remainingSeats = 0L placeholder)
        EventDetailResponseDto cached = eventDetailCacheService.getCachedEventDetail(eventId);

        // ② 섹션별 잔여 좌석 수 실시간 조회 → 새 SectionDetailDto 생성(toBuilder 미지원)
        List<EventDetailResponseDto.SectionDetailDto> sectionsWithSeats =
                cached.getSections().stream()
                        .map(s -> EventDetailResponseDto.SectionDetailDto.builder()
                                .sectionId(s.getSectionId())
                                .sectionName(s.getSectionName())
                                .price(s.getPrice())
                                .totalSeats(s.getTotalSeats())
                                .remainingSeats(seatRepository.countAvailableSeatsBySectionId(s.getSectionId()))
                                .build())
                        .collect(Collectors.toList());

        // ③ 기본 정보 + 실시간 잔여 좌석 수 조합
        return EventDetailResponseDto.builder()
                .eventId(cached.getEventId())
                .title(cached.getTitle())
                .category(cached.getCategory())
                .venue(cached.getVenue())
                .eventDate(cached.getEventDate())
                .saleStartAt(cached.getSaleStartAt())
                .saleEndAt(cached.getSaleEndAt())
                .description(cached.getDescription())
                .thumbnailUrl(cached.getThumbnailUrl())
                .sections(sectionsWithSeats)
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

    /**
     * 이벤트 ID로 구역별 좌석 상태 조회
     * 좌석 상태 판단 (SA 문서 FN-SEAT-01 기준):
     *   1. ACTIVE_BOOKING 존재 → CONFIRMED
     *   2. Redis hold:{eventId}:{seatId} 키 존재 → ON_HOLD
     *   3. 그 외 → AVAILABLE
     *
     * @param eventId   조회할 이벤트 ID
     * @param sectionId null이면 전체 구역, 값이 있으면 해당 구역만
     */
    public SeatResponseDto getSeatsByEventId(Long eventId, Long sectionId) {

        // 1. 이벤트 존재 여부 확인
        Event event = eventRepository.findByIdWithVenue(eventId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EVENT_NOT_FOUND));

        // 2. 좌석 목록 조회 (섹션 필터 분기)
        List<Seat> seats = (sectionId != null)
                ? seatRepository.findAllBySectionIdWithSection(sectionId)
                : seatRepository.findAllByEventIdWithSection(eventId);

        // 3. CONFIRMED 좌석 ID Set 조회 (ACTIVE_BOOKING 기반)
        Set<Long> confirmedSeatIds = new HashSet<>(
                seatRepository.findConfirmedSeatIdsByEventId(eventId)
        );

        // 4. 섹션별 그룹핑
        Map<Long, List<Seat>> seatsBySection = seats.stream()
                .collect(Collectors.groupingBy(s -> s.getSection().getSectionId()));

        // 5. 섹션 순서 유지를 위해 섹션 정보 추출
        List<SeatResponseDto.SectionSeatDto> sectionDtos = seatsBySection.entrySet().stream()
                .map(entry -> {
                    Section section = entry.getValue().get(0).getSection();
                    List<SeatResponseDto.SeatDto> seatDtos = entry.getValue().stream()
                            .map(seat -> {
                                SeatResponseDto.SeatStatus status = resolveSeatStatus(
                                        seat.getSeatId(), eventId, confirmedSeatIds
                                );
                                return SeatResponseDto.SeatDto.builder()
                                        .seatId(seat.getSeatId())
                                        .rowName(seat.getRowName())
                                        .colNum(seat.getColNum())
                                        .seatNumber(seat.getRowName() + "-" + seat.getColNum())
                                        .status(status)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return SeatResponseDto.SectionSeatDto.builder()
                            .sectionId(section.getSectionId())
                            .sectionName(section.getSectionName())
                            .price(section.getPrice())
                            .seats(seatDtos)
                            .build();
                })
                .collect(Collectors.toList());

        return SeatResponseDto.builder()
                .eventId(eventId)
                .sections(sectionDtos)
                .build();
    }

    /**
     * 좌석 상태 결정 로직
     * CONFIRMED 우선 → ON_HOLD → AVAILABLE
     */
    private SeatResponseDto.SeatStatus resolveSeatStatus(
            Long seatId, Long eventId, Set<Long> confirmedSeatIds) {

        if (confirmedSeatIds.contains(seatId)) {
            return SeatResponseDto.SeatStatus.CONFIRMED;
        }

        // Redis hold:{eventId}:{seatId} 키 존재 여부 확인
        String holdKey = "hold:" + eventId + ":" + seatId;
        Boolean isOnHold = redisTemplate.hasKey(holdKey);
        if (Boolean.TRUE.equals(isOnHold)) {
            return SeatResponseDto.SeatStatus.ON_HOLD;
        }

        return SeatResponseDto.SeatStatus.AVAILABLE;
    }
}
