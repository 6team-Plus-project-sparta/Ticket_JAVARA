package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.EventCategory;
import com.example.ticket_javara.domain.event.entity.EventStatus;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Event Repository Custom 인터페이스
 * QueryDSL을 사용한 복잡한 쿼리 처리
 */
public interface EventRepositoryCustom {

    /**
     * minPrice 정렬을 포함한 이벤트 목록 조회
     * - Section 테이블과 LEFT JOIN하여 MIN(price) 계산
     * - DB 레벨에서 정렬 및 페이징 처리
     * 
     * @param category 카테고리 필터 (nullable)
     * @param status 상태 필터 (nullable)
     * @param pageable 페이징 및 정렬 정보
     * @return 이벤트 요약 정보 페이지
     */
    Page<EventSummaryResponseDto> findEventsWithMinPrice(
            EventCategory category,
            EventStatus status,
            Pageable pageable
    );
}
