package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {
    @Query("SELECT s FROM Section s WHERE s.event.eventId IN :eventIds")
    List<Section> findAllByEventIds(@Param("eventIds") List<Long> eventIds);
}
