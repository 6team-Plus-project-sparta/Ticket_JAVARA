package com.example.ticket_javara.domain.search.service;

import com.example.ticket_javara.domain.event.entity.*;
import com.example.ticket_javara.domain.event.repository.SeatRepository;
import com.example.ticket_javara.domain.search.dto.request.SearchRequestDto;
import com.example.ticket_javara.domain.search.repository.EventSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * SRCH-S-11 ~ 12: Redis Cache-Aside 도전 기능 테스트
 *
 * 전제 조건 (이 테스트 실행 전 반드시 확인):
 * 1. application.yml에서 cache.provider=redis로 설정되어 있어야 함
 * 2. CacheConfig에 @ConditionalOnProperty(havingValue="redis") RedisCacheManager Bean이 있어야 함
 * 3. Docker Redis가 실행 중이어야 함 (localhost:6379)
 *
 * Caffeine 테스트(SRCH-S-09, 10)와 검증 패턴이 동일
 * 차이점: 프로파일만 test-redis로 변경 → RedisCacheManager 활성화
 *
 * Cache-Aside 패턴 흐름 (SA 문서 FN-SRCH-03):
 * MISS: Redis GET → null → DB 조회 → Redis SET(TTL 5분) → 반환
 * HIT:  Redis GET → 캐시된 데이터 → 즉시 반환 (DB 미접근)
 */
@SpringBootTest
@ActiveProfiles("test")//test-redis대신 임시진행
@TestPropertySource(properties = {
        "cache.provider=redis"  // 이것만 Redis로 오버라이드
})
@DisplayName("SRCH-S-11 ~ 12: Redis Cache-Aside 도전 기능 테스트")
class SearchServiceRedisTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private CacheManager cacheManager;  // RedisCacheManager가 주입되어야 함

    @MockitoBean
    private EventSearchRepository eventSearchRepository;

    @MockitoBean
    private SeatRepository seatRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;//  실제 연결 (Redis 키 삭제 + ZINCRBY 실제 동작)

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ⚠️ RedisTemplate은 MockitoBean 처리하지 않음
    // → 실제 Redis 연결 사용 (Cache-Aside 실제 동작 검증을 위해)

    @BeforeEach
    void setUp() {
        // Redis에 남아있는 캐시 키 전체 삭제
        Set<String> keys = redisTemplate.keys("event-search*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // seatRepository 스텁
        lenient().when(seatRepository.countAvailableSeatsByEventId(anyLong()))
                .thenReturn(200L);
    }

    // ───────────────────────────────────────────────
    // 공통 픽스처
    // ───────────────────────────────────────────────

    private Event sampleEvent() {
        Venue venue = Venue.builder()
                .name("KSPO DOME")
                .build();

        Section section = Section.builder()
                .price(50000)
                .build();

        Event event = Event.builder()
                .title("세븐틴 콘서트")
                .category(EventCategory.CONCERT)
                .venue(venue)
                .eventDate(LocalDateTime.of(2026, 5, 1, 19, 0))
                .thumbnailUrl("https://cdn.ticketflow.io/events/seventeen.jpg")
                .roundNumber(1)
                .status(EventStatus.ON_SALE)
                .saleStartAt(LocalDateTime.of(2026, 4, 1, 0, 0))
                .saleEndAt(LocalDateTime.of(2026, 5, 1, 18, 0))
                .build();

        event.getSections().add(section);
        return event;
    }

    private Page<Event> samplePage() {
        return new PageImpl<>(List.of(sampleEvent()));
    }

    /**
     * SRCH-S-11
     * Cache-Aside MISS → DB 조회 → Redis에 캐시 저장
     *
     * 검증 방법:
     * - 동일 조건 2회 호출 시 Repository는 1회만 호출
     * - 2번째 호출은 Redis 캐시 HIT → DB 미접근
     *
     * Caffeine(SRCH-S-09)과 검증 패턴 동일
     * 차이점: 캐시 저장소가 JVM 힙이 아닌 Redis
     */
    @Test
    @DisplayName("SRCH-S-11: Cache-Aside MISS 시 DB 조회 후 Redis에 캐시가 저장된다")
    void searchEventsV2_redis_cacheMiss_dbQueriedAndCachedInRedis() {
        // given
        SearchRequestDto request = SearchRequestDto.builder()
                .keyword("세븐틴")
                .build();

        given(eventSearchRepository.searchEvents(
                any(SearchRequestDto.class), any(Pageable.class)))
                .willReturn(samplePage());

        // when — 동일 조건 2회 호출
        // 1회차: Redis MISS → DB 조회 → Redis SET
        searchService.searchEventsV2(request, PageRequest.of(0, 20));
        // 2회차: Redis HIT → DB 미접근
        searchService.searchEventsV2(request, PageRequest.of(0, 20));

        // then — Repository는 1회만 호출 (2번째는 Redis HIT)
        verify(eventSearchRepository, times(1))
                .searchEvents(any(SearchRequestDto.class), any(Pageable.class));
    }

    /**
     * SRCH-S-12
     * Cache-Aside HIT → DB 미조회
     *
     * 검증 방법:
     * - 첫 번째 호출로 Redis에 캐시 적재
     * - 두 번째 호출 시 Repository 호출 없음 확인
     * - cacheManager로 Redis 캐시 키 존재 여부 추가 확인
     */
    @Test
    @DisplayName("SRCH-S-12: Cache-Aside HIT 시 Repository가 호출되지 않는다")
    void searchEventsV2_redis_cacheHit_repositoryNotCalled() {
        // given
        SearchRequestDto request = SearchRequestDto.builder()
                .keyword("세븐틴")
                .build();

        given(eventSearchRepository.searchEvents(
                any(SearchRequestDto.class), any(Pageable.class)))
                .willReturn(samplePage());

        // when
        // 1회차: MISS → DB 조회 + Redis 캐시 저장
        searchService.searchEventsV2(request, PageRequest.of(0, 20));

        // 캐시가 Redis에 저장됐는지 확인
        var cache = cacheManager.getCache("event-search");
        assert cache != null : "RedisCacheManager가 활성화되지 않음 — cache.provider=redis 확인 필요";

        // 2회차: HIT → DB 미접근
        searchService.searchEventsV2(request, PageRequest.of(0, 20));

        // then
        verify(eventSearchRepository, times(1))
                .searchEvents(any(SearchRequestDto.class), any(Pageable.class));
    }

    /**
     * SRCH-S-12 확장
     * 캐시 무효화(evictSearchCache) 후 Redis에서 키가 삭제되고 DB 재조회되는지 검증
     *
     * SA 문서 FN-SRCH-03: 무효화 방식 — SCAN + DEL (KEYS 사용 금지)
     */
    @Test
    @DisplayName("SRCH-S-12 확장: 캐시 무효화 후 Redis 재조회 시 DB가 다시 호출된다")
    void searchEventsV2_redis_afterEvict_repositoryCalledAgain() {
        // given
        SearchRequestDto request = SearchRequestDto.builder()
                .keyword("세븐틴")
                .build();

        given(eventSearchRepository.searchEvents(
                any(SearchRequestDto.class), any(Pageable.class)))
                .willReturn(samplePage());

        // when
        searchService.searchEventsV2(request, PageRequest.of(0, 20)); // 1회: MISS → Redis 저장
        searchService.evictSearchCache();                               // Redis SCAN+DEL 무효화
        searchService.searchEventsV2(request, PageRequest.of(0, 20)); // 2회: MISS → DB 재조회

        // then — 총 2회 호출
        verify(eventSearchRepository, times(2))
                .searchEvents(any(SearchRequestDto.class), any(Pageable.class));
    }
}
