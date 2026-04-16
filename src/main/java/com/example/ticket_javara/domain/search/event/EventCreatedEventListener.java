package com.example.ticket_javara.domain.search.event;

import com.example.ticket_javara.domain.search.service.SearchService;
import com.example.ticket_javara.global.event.EventCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventCreatedEventListener {

    private final SearchService searchService;

    /**
     * @Async — 캐시 삭제가 이벤트 등록 API 응답을 블로킹하지 않도록
     * 캐시 삭제 실패해도 이벤트 등록 자체는 이미 커밋된 상태이므로 영향 없음
     */
    @Async
    @EventListener
    public void onEventCreated(EventCreatedEvent event) {
        log.info("[EventCreatedEventListener] 이벤트 등록 감지 — search 캐시 무효화 시작");
        searchService.evictSearchCache();
    }
}
