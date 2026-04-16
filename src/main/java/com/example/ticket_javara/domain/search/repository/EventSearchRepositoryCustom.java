package com.example.ticket_javara.domain.search.repository;

import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.search.dto.request.SearchRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventSearchRepositoryCustom {
    Page<Event> searchEvents(SearchRequestDto condition, Pageable pageable);
}
