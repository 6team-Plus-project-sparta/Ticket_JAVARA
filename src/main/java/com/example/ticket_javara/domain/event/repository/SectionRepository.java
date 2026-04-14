package com.example.ticket_javara.domain.event.repository;

import com.example.ticket_javara.domain.event.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {
}
