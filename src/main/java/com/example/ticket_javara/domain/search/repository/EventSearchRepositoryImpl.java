package com.example.ticket_javara.domain.search.repository;

import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.entity.QEvent;
import com.example.ticket_javara.domain.event.entity.QSection;
import com.example.ticket_javara.domain.event.entity.QVenue;
import com.example.ticket_javara.domain.search.dto.request.SearchRequestDto;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalTime;
import java.util.List;

@RequiredArgsConstructor
public class EventSearchRepositoryImpl implements EventSearchRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Event> searchEvents(SearchRequestDto condition, Pageable pageable) {
        QEvent event = QEvent.event;
        QVenue venue = QVenue.venue;
        QSection section = QSection.section;
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.getKeyword() != null && !condition.getKeyword().trim().isEmpty()) {
            builder.and(event.title.containsIgnoreCase(condition.getKeyword()));
        }

        if (condition.getCategory() != null) {
            builder.and(event.category.eq(condition.getCategory()));
        }

        if (condition.getStartDate() != null) {
            builder.and(event.eventDate.goe(condition.getStartDate().atStartOfDay()));
        }

        if (condition.getEndDate() != null) {
            builder.and(event.eventDate.loe(condition.getEndDate().atTime(LocalTime.MAX)));
        }

        if (condition.getMinPrice() != null || condition.getMaxPrice() != null) {
            BooleanBuilder priceBuilder = new BooleanBuilder();
            priceBuilder.and(section.event.eq(event));
            
            if (condition.getMinPrice() != null) {
                priceBuilder.and(section.price.goe(condition.getMinPrice()));
            }
            if (condition.getMaxPrice() != null) {
                priceBuilder.and(section.price.loe(condition.getMaxPrice()));
            }
            
            builder.and(JPAExpressions.selectOne()
                    .from(section)
                    .where(priceBuilder)
                    .exists());
        }

        JPAQuery<Event> query = queryFactory.selectFrom(event)
                .join(event.venue, venue).fetchJoin()//N + 1방지
                .where(builder);

        pageable.getSort().forEach(order -> {
            if (order.getProperty().equals("eventDate")) {
                if (order.isAscending()) {
                    query.orderBy(event.eventDate.asc());
                } else {
                    query.orderBy(event.eventDate.desc());
                }
            }
        });

        Long total = queryFactory.select(event.count())
                .from(event)
                .where(builder)
                .fetchOne();
        if (total == null) total = 0L;

        List<Event> events = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(events, pageable, total);
    }
}
