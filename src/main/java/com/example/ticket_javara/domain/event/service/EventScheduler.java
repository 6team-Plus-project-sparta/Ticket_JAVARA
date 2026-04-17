package com.example.ticket_javara.domain.event.service;

import com.example.ticket_javara.domain.event.entity.EventStatus;
import com.example.ticket_javara.domain.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventScheduler {

    private final EventRepository eventRepository;

    /**
     * 매 정각 실행 — eventDate 경과한 ON_SALE/SOLD_OUT 이벤트를 ENDED로 전환
     * cron = "초 분 시 일 월 요일"
     */
    @Scheduled(cron = "0 0 * * * *")  // 매 정각 실행
    @Transactional
    @CacheEvict(value = "event-detail", allEntries = true)  // 캐시 무효화
    public void updateEndedEvents() {
        int updatedCount = eventRepository.bulkUpdateEndedStatus(
                LocalDateTime.now(),
                EventStatus.ENDED
        );
        log.info("[EventScheduler] ENDED 상태 전환 완료 — {}건", updatedCount);
    }
}
