package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.EventCategory;
import com.example.ticket_javara.domain.event.entity.EventStatus;
import com.example.ticket_javara.domain.event.entity.QEvent;
import com.example.ticket_javara.domain.event.entity.QSection;
import com.example.ticket_javara.domain.event.entity.QVenue;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

/**
 * EventRepository QueryDSL 구현체
 * - minPrice 정렬을 위한 복잡한 쿼리 처리
 * - DB 레벨에서 JOIN + GROUP BY + MIN(price) 계산
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

        // 1단계: 이벤트 ID와 minPrice를 먼저 조회 (서브쿼리 사용)
        QSection subSection = new QSection("subSection");
        
        List<com.querydsl.core.Tuple> tuples = queryFactory
                .select(
                        event.eventId,
                        JPAExpressions
                                .select(subSection.price.min())
                                .from(subSection)
                                .where(subSection.event.eventId.eq(event.eventId))
                )
                .from(event)
                .where(
                        categoryEq(category),
                        statusEq(status),
                        event.status.ne(EventStatus.DELETED)
                )
                .fetch();

        // 전체 개수
        long total = tuples.size();
        
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // minPrice로 정렬
        Sort.Order minPriceOrder = pageable.getSort().getOrderFor("minPrice");
        if (minPriceOrder != null) {
            tuples.sort((a, b) -> {
                Integer priceA = a.get(1, Integer.class);
                Integer priceB = b.get(1, Integer.class);
                if (priceA == null) priceA = 0;
                if (priceB == null) priceB = 0;
                return minPriceOrder.isAscending() 
                    ? priceA.compareTo(priceB) 
                    : priceB.compareTo(priceA);
            });
        }

        // 페이징 적용
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), tuples.size());
        
        if (start >= tuples.size()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        
        List<com.querydsl.core.Tuple> pagedTuples = tuples.subList(start, end);
        List<Long> pagedEventIds = pagedTuples.stream()
                .map(tuple -> tuple.get(0, Long.class))
                .toList();

        // 2단계: 페이징된 이벤트 ID로 전체 정보 조회
        List<EventSummaryResponseDto> content = queryFactory
                .select(Projections.constructor(
                        EventSummaryResponseDto.class,
                        event.eventId,
                        event.title,
                        event.category,
                        venue.name,
                        event.eventDate,
                        Expressions.constant(0), // minPrice (나중에 채움)
                        Expressions.constant(0L), // remainingSeats (나중에 채움)
                        event.thumbnailUrl,
                        event.status
                ))
                .from(event)
                .join(event.venue, venue)
                .where(event.eventId.in(pagedEventIds))
                .fetch();

        // minPrice 매핑
        java.util.Map<Long, Integer> minPriceMap = pagedTuples.stream()
                .collect(java.util.stream.Collectors.toMap(
                        tuple -> tuple.get(0, Long.class),
                        tuple -> {
                            Integer price = tuple.get(1, Integer.class);
                            return price != null ? price : 0;
                        }
                ));

        // 잔여 좌석 수 조회
        List<Object[]> seatCounts = seatRepository.countAvailableSeatsByEventIds(pagedEventIds);
        java.util.Map<Long, Long> seatCountMap = seatCounts.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        // DTO에 값 설정 및 정렬 순서 유지
        content.forEach(dto -> {
            dto.setMinPrice(minPriceMap.getOrDefault(dto.getEventId(), 0));
            dto.setRemainingSeats(seatCountMap.getOrDefault(dto.getEventId(), 0L));
        });

        // 원래 정렬 순서대로 재정렬
        java.util.Map<Long, EventSummaryResponseDto> dtoMap = content.stream()
                .collect(java.util.stream.Collectors.toMap(
                        EventSummaryResponseDto::getEventId,
                        dto -> dto
                ));
        
        List<EventSummaryResponseDto> sortedContent = pagedEventIds.stream()
                .map(dtoMap::get)
                .filter(dto -> dto != null)
                .collect(java.util.stream.Collectors.toList());

        return new PageImpl<>(sortedContent, pageable, total);
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
