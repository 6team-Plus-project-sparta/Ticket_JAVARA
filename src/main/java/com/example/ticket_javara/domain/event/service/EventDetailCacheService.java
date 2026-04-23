package com.example.ticket_javara.domain.event.service;

import com.example.ticket_javara.domain.event.dto.response.EventDetailResponseDto;
import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.entity.Section;
import com.example.ticket_javara.domain.event.repository.EventRepository;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 이벤트 상세 캐시 전용 Bean (SA 문서 FN-EVT-03 기준)
 *
 * ✅ 캐시 적용 대상 — 이벤트 기본 정보
 *    : title, venue, sections(sectionId/name/price/totalSeats)
 *
 * ❌ 캐시 제외 대상 — 실시간 조회 필요
 *    : remainingSeats → EventService.getEventDetail()에서 캐시 밖에서 주입
 *
 * ⚠️ self-invocation 문제 방지:
 *    @Cacheable 메서드를 같은 클래스 내부에서 호출하면 Spring AOP 프록시가 우회됩니다.
 *    이를 방지하기 위해 이 클래스를 별도 Bean으로 분리합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventDetailCacheService {

    private final EventRepository eventRepository;

    /**
     * 이벤트 기본 정보만 캐시 (TTL 10분 — CacheConfig 설정 기준)
     * remainingSeats는 0L placeholder로 채움 → 외부에서 실시간 값으로 교체
     */
    @Cacheable(value = "event-detail", key = "#eventId")
    public EventDetailResponseDto getCachedEventDetail(Long eventId) {
        Event event = eventRepository.findByIdWithVenueAndSectionsExcludeDeleted(eventId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EVENT_NOT_FOUND));

        List<EventDetailResponseDto.SectionDetailDto> sectionDtos = event.getSections().stream()
                .map(section -> EventDetailResponseDto.SectionDetailDto.builder()
                        .sectionId(section.getSectionId())
                        .sectionName(section.getSectionName())
                        .price(section.getPrice())
                        .totalSeats(section.getTotalSeats())
                        .remainingSeats(0L)  // placeholder — 실시간 값은 EventService에서 주입
                        .build())
                .collect(Collectors.toList());

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
                .saleStartAt(event.getSaleStartAt())
                .saleEndAt(event.getSaleEndAt())
                .description(event.getDescription())
                .thumbnailUrl(event.getThumbnailUrl())
                .sections(sectionDtos)
                .build();
    }
}
