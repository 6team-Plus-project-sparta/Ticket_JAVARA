package com.example.ticket_javara.domain.search.repository;

import com.example.ticket_javara.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSearchRepository extends JpaRepository<Event, Long>, EventSearchRepositoryCustom {
}
