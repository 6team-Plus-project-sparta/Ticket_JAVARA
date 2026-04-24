package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.*;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
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
 * - DB 레벨에서 LEFT JOIN + GROUP BY로 minPrice 계산 및 정렬
 * - 페이징도 DB 레벨에서 완료
 */
@RequiredArgsConstructor
public class EventRepositoryImpl implements EventRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final SeatRepository seatRepository;

    @Override
    public Page<EventSummaryResponseDto> findEventsWithMinPrice(
            EventCategory category,
            EventStatus status,
            Pageable pageable) {

        QEvent event = QEvent.event;
        QVenue venue = QVenue.venue;
        QSection section = QSection.section;

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

        // DB 레벨에서 LEFT JOIN + GROUP BY로 minPrice 계산 및 정렬
        List<EventSummaryResponseDto> content = queryFactory
                .select(Projections.constructor(
                        EventSummaryResponseDto.class,
                        event.eventId,
                        event.title,
                        event.category,
                        venue.name,
                        event.eventDate,
                        section.price.min().coalesce(0), // DB에서 바로 최저가 계산
                        Expressions.constant(0L), // remainingSeats는 별도 조회
                        event.thumbnailUrl,
                        event.status
                ))
                .from(event)
                .join(event.venue, venue)
                .leftJoin(event.sections, section) // Section과 LEFT JOIN
                .where(
                        categoryEq(category),
                        statusEq(status),
                        event.status.ne(EventStatus.DELETED)
                )
                .groupBy(event.eventId, event.title, event.category, venue.name, 
                         event.eventDate, event.thumbnailUrl, event.status) // GROUP BY 필수
                .orderBy(getOrderSpecifiers(pageable, event, section).toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // remainingSeats 일괄 조회 및 설정
        if (!content.isEmpty()) {
            List<Long> eventIds = content.stream()
                    .map(EventSummaryResponseDto::getEventId)
                    .toList();

            List<Object[]> seatCounts = seatRepository.countAvailableSeatsByEventIds(eventIds);
            Map<Long, Long> seatCountMap = seatCounts.stream()
                    .collect(Collectors.toMap(
                            row -> (Long) row[0],
                            row -> (Long) row[1]
                    ));

            // Builder를 사용하여 새 객체 생성 (불변성 유지)
            content = content.stream()
                    .map(dto -> EventSummaryResponseDto.builder()
                            .eventId(dto.getEventId())
                            .title(dto.getTitle())
                            .category(dto.getCategory())
                            .venueName(dto.getVenueName())
                            .eventDate(dto.getEventDate())
                            .minPrice(dto.getMinPrice())
                            .remainingSeats(seatCountMap.getOrDefault(dto.getEventId(), 0L))
                            .thumbnailUrl(dto.getThumbnailUrl())
                            .eventStatus(dto.getEventStatus())
                            .build())
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 정렬 조건 생성
     */
    private List<OrderSpecifier<?>> getOrderSpecifiers(
            Pageable pageable,
            QEvent event,
            QSection section) {

        List<OrderSpecifier<?>> orders = new ArrayList<>();

        for (Sort.Order order : pageable.getSort()) {
            switch (order.getProperty()) {
                case "minPrice" -> {
                    if (order.isAscending()) {
                        orders.add(section.price.min().coalesce(0).asc());
                    } else {
                        orders.add(section.price.min().coalesce(0).desc());
                    }
                }
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
