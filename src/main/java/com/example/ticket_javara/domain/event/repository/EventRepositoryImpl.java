package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.*;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EventRepository QueryDSL 구현체
 * - DB 레벨에서 정렬 및 페이징 완료
 * - LEFT JOIN으로 minPrice 계산
 */
@RequiredArgsConstructor
public class EventRepositoryImpl implements EventRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final SeatRepository seatRepository;
    private final SectionRepository sectionRepository;

    @Override
    public Page<EventSummaryResponseDto> findEventsWithMinPrice(
            EventCategory category,
            EventStatus status,
            Pageable pageable) {

        QEvent event = QEvent.event;
        QVenue venue = QVenue.venue;

        // 전체 개수 조회
        Long totalCount = queryFactory
                .select(event.count())
                .from(event)
                .where(
                        categoryEq(category),
                        statusEq(status),
                        event.status.ne(EventStatus.DELETED)
                )
                .fetchOne();

        long total = (totalCount != null) ? totalCount : 0L;

        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // minPrice 정렬이 필요한지 확인
        boolean needsMinPriceSort = pageable.getSort().stream()
                .anyMatch(order -> "minPrice".equals(order.getProperty()));

        List<EventSummaryResponseDto> content;

        if (needsMinPriceSort) {
            // minPrice 정렬이 필요한 경우: 모든 이벤트의 minPrice를 먼저 계산
            List<Long> allEventIds = queryFactory
                    .select(event.eventId)
                    .from(event)
                    .where(
                            categoryEq(category),
                            statusEq(status),
                            event.status.ne(EventStatus.DELETED)
                    )
                    .fetch();

            if (allEventIds.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }

            // 각 이벤트의 minPrice 계산
            Map<Long, Integer> minPriceMap = sectionRepository.findAllByEventIds(allEventIds)
                    .stream()
                    .collect(Collectors.groupingBy(
                            s -> s.getEvent().getEventId(),
                            Collectors.collectingAndThen(
                                    Collectors.minBy((s1, s2) -> Integer.compare(s1.getPrice(), s2.getPrice())),
                                    opt -> opt.map(Section::getPrice).orElse(0)
                            )
                    ));

            // eventId와 minPrice 쌍으로 정렬
            List<Map.Entry<Long, Integer>> sortedEntries = new ArrayList<>(minPriceMap.entrySet());
            Sort.Order minPriceOrder = pageable.getSort().getOrderFor("minPrice");
            if (minPriceOrder != null) {
                if (minPriceOrder.isAscending()) {
                    sortedEntries.sort(Map.Entry.comparingByValue());
                } else {
                    sortedEntries.sort(Map.Entry.<Long, Integer>comparingByValue().reversed());
                }
            }

            // 페이징 적용
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), sortedEntries.size());
            
            if (start >= sortedEntries.size()) {
                return new PageImpl<>(List.of(), pageable, total);
            }

            List<Long> pagedEventIds = sortedEntries.subList(start, end).stream()
                    .map(Map.Entry::getKey)
                    .toList();

            // 페이징된 이벤트 정보 조회
            List<Event> events = queryFactory
                    .selectFrom(event)
                    .join(event.venue, venue).fetchJoin()
                    .where(event.eventId.in(pagedEventIds))
                    .fetch();

            // 잔여 좌석 수 조회
            List<Object[]> seatCounts = seatRepository.countAvailableSeatsByEventIds(pagedEventIds);
            Map<Long, Long> seatCountMap = seatCounts.stream()
                    .collect(Collectors.toMap(
                            row -> (Long) row[0],
                            row -> (Long) row[1]
                    ));

            // DTO 생성 및 정렬 순서 유지
            Map<Long, Event> eventMap = events.stream()
                    .collect(Collectors.toMap(Event::getEventId, e -> e));

            content = pagedEventIds.stream()
                    .map(eventId -> {
                        Event e = eventMap.get(eventId);
                        if (e == null) return null;
                        return EventSummaryResponseDto.builder()
                                .eventId(e.getEventId())
                                .title(e.getTitle())
                                .category(e.getCategory())
                                .venueName(e.getVenue().getName())
                                .eventDate(e.getEventDate())
                                .minPrice(minPriceMap.getOrDefault(eventId, 0))
                                .remainingSeats(seatCountMap.getOrDefault(eventId, 0L))
                                .thumbnailUrl(e.getThumbnailUrl())
                                .eventStatus(e.getStatus())
                                .build();
                    })
                    .filter(dto -> dto != null)
                    .collect(Collectors.toList());

        } else {
            // minPrice 정렬이 아닌 경우: 기존 방식 사용
            List<OrderSpecifier<?>> orders = getOrderSpecifiers(pageable, event);
            
            List<Event> events = queryFactory
                    .selectFrom(event)
                    .join(event.venue, venue).fetchJoin()
                    .where(
                            categoryEq(category),
                            statusEq(status),
                            event.status.ne(EventStatus.DELETED)
                    )
                    .orderBy(orders.toArray(new OrderSpecifier[0]))
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize())
                    .fetch();

            if (events.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, total);
            }

            List<Long> eventIds = events.stream()
                    .map(Event::getEventId)
                    .toList();

            // minPrice 계산
            Map<Long, Integer> minPriceMap = sectionRepository.findAllByEventIds(eventIds)
                    .stream()
                    .collect(Collectors.groupingBy(
                            s -> s.getEvent().getEventId(),
                            Collectors.collectingAndThen(
                                    Collectors.minBy((s1, s2) -> Integer.compare(s1.getPrice(), s2.getPrice())),
                                    opt -> opt.map(Section::getPrice).orElse(0)
                            )
                    ));

            // 잔여 좌석 수 조회
            List<Object[]> seatCounts = seatRepository.countAvailableSeatsByEventIds(eventIds);
            Map<Long, Long> seatCountMap = seatCounts.stream()
                    .collect(Collectors.toMap(
                            row -> (Long) row[0],
                            row -> (Long) row[1]
                    ));

            content = events.stream()
                    .map(e -> EventSummaryResponseDto.builder()
                            .eventId(e.getEventId())
                            .title(e.getTitle())
                            .category(e.getCategory())
                            .venueName(e.getVenue().getName())
                            .eventDate(e.getEventDate())
                            .minPrice(minPriceMap.getOrDefault(e.getEventId(), 0))
                            .remainingSeats(seatCountMap.getOrDefault(e.getEventId(), 0L))
                            .thumbnailUrl(e.getThumbnailUrl())
                            .eventStatus(e.getStatus())
                            .build())
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 정렬 조건 생성 (minPrice 제외)
     */
    private List<OrderSpecifier<?>> getOrderSpecifiers(
            Pageable pageable,
            QEvent event) {

        List<OrderSpecifier<?>> orders = new ArrayList<>();

        for (Sort.Order order : pageable.getSort()) {
            switch (order.getProperty()) {
                case "eventDate" -> {
                    if (order.isAscending()) {
                        orders.add(event.eventDate.asc());
                    } else {
                        orders.add(event.eventDate.desc());
                    }
                }
                case "createdAt" -> {
                    if (order.isAscending()) {
                        orders.add(event.createdAt.asc());
                    } else {
                        orders.add(event.createdAt.desc());
                    }
                }
            }
        }

        // 기본 정렬: createdAt desc
        if (orders.isEmpty()) {
            orders.add(event.createdAt.desc());
        }

        return orders;
    }

    /**
     * 카테고리 조건
     */
    private BooleanExpression categoryEq(EventCategory category) {
        return category != null ? QEvent.event.category.eq(category) : null;
    }

    /**
     * 상태 조건
     */
    private BooleanExpression statusEq(EventStatus status) {
        return status != null ? QEvent.event.status.eq(status) : null;
    }
}
