package com.example.ticket_javara.domain.search.service;

import com.example.ticket_javara.domain.event.entity.*;
import com.example.ticket_javara.domain.event.repository.SeatRepository;
import com.example.ticket_javara.domain.search.dto.request.SearchRequestDto;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.example.ticket_javara.domain.search.dto.response.PopularKeywordResponseDto;
import com.example.ticket_javara.domain.search.repository.EventSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * SearchService v2 테스트 — SRCH-S-09 ~ 17
 *
 * @SpringBootTest 사용 이유:
 * - @Cacheable은 Spring AOP 프록시로 동작하므로
 *   실제 Spring 컨텍스트가 있어야 캐시 HIT/MISS 검증 가능
 *
 * 테스트 범위:
 * - SRCH-S-09 ~ 10 : Caffeine 캐시 동작 검증
 * - SRCH-S-11 ~ 12 : 도전 기능 (Redis Cache-Aside) — 주석으로 가이드 제공
 * - SRCH-S-13 ~ 15 : 인기 검색어 ZSet 조회
 * - SRCH-S-16 ~ 17 : 페이징 / 정렬
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SearchService v2 테스트 — SRCH-S-09 ~ 17")
class SearchServiceCacheAndPopularTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private EventSearchRepository eventSearchRepository;

    @MockitoBean
    private SeatRepository seatRepository;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ZSetOperations<String, String> zSetOperations;

    private static final String POPULAR_KEY = "search-keywords";

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
     * 날짜/가격 조건으로 이벤트 생성
     * SRCH-S-17 정렬 검증에 사용
     */
    private Event eventWithDate(String title, LocalDateTime date, int price) {
        Venue venue = Venue.builder().name("테스트 공연장").build();
        Section section = Section.builder().price(price).build();

        Event event = Event.builder()
                .title(title)
                .category(EventCategory.CONCERT)
                .venue(venue)
                .eventDate(date)
                .thumbnailUrl("https://cdn.ticketflow.io/events/test.jpg")
                .roundNumber(1)
                .status(EventStatus.ON_SALE)
                .saleStartAt(date.minusDays(30))
                .saleEndAt(date.minusHours(1))
                .build();

        event.getSections().add(section);
        return event;
    }

    @BeforeEach
    void setUp() {
        // 테스트 간 캐시 오염 방지 — 각 테스트 전 캐시 전체 초기화
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });

        // stringRedisTemplate ZSet 스텁 — NPE 방지
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // seatRepository 기본 스텁 — mapToSummary() NPE 방지
        lenient().when(seatRepository.countAvailableSeatsByEventId(anyLong())).thenReturn(200L);
    }

    // ═══════════════════════════════════════════════════════
    // SRCH-S-09 ~ 10 : Caffeine 캐시 동작 검증
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("SRCH-S-09 ~ 10 : Caffeine 캐시 동작 검증")
    class CaffeineTests {

        /**
         * SRCH-S-09
         * 동일 조건으로 searchEventsV2() 2회 호출 시 Repository는 1회만 조회
         * 두 번째 호출은 Caffeine 캐시 HIT → DB 미접근
         *
         * @Cacheable AOP가 동작해야 하므로 @SpringBootTest 필수
         * @ExtendWith(MockitoExtension)만으로는 프록시가 생성되지 않아 항상 MISS
         */
        @Test
        @DisplayName("SRCH-S-09: 동일 조건 2회 호출 시 Repository는 1회만 조회된다")
        void searchEventsV2_sameConditionTwice_repositoryCalledOnce() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("세븐틴")
                    .build();

            given(eventSearchRepository.searchEvents(
                    any(SearchRequestDto.class), any(Pageable.class)))
                    .willReturn(samplePage());

            // when — 동일 조건 2회 호출
            searchService.searchEventsV2(request, PageRequest.of(0, 20));
            searchService.searchEventsV2(request, PageRequest.of(0, 20));

            // then — 2번째는 캐시 HIT이므로 Repository는 1회만 호출
            verify(eventSearchRepository, times(1))
                    .searchEvents(any(SearchRequestDto.class), any(Pageable.class));
        }

        /**
         * SRCH-S-10
         * 캐시 무효화 후 재조회 시 Repository가 다시 호출되는지 검증
         *
         * evictSearchCache()는 Redis SCAN+DEL 방식 (Caffeine 직접 무효화 불가)
         * → CacheManager.getCache().clear()로 직접 무효화 후 재조회 검증
         *
         * 시나리오:
         * 1. searchEventsV2() → Repository 조회 + 캐시 PUT (1회)
         * 2. cache.clear() → 캐시 무효화 (이벤트 등록 상황 시뮬레이션)
         * 3. searchEventsV2() → 캐시 MISS → Repository 재조회 (2회)
         */
        @Test
        @DisplayName("SRCH-S-10: 캐시 무효화 후 재조회 시 Repository가 다시 호출된다")
        void searchEventsV2_afterCacheEvict_repositoryCalledAgain() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("세븐틴")
                    .build();

            given(eventSearchRepository.searchEvents(
                    any(SearchRequestDto.class), any(Pageable.class)))
                    .willReturn(samplePage());

            // when
            searchService.searchEventsV2(request, PageRequest.of(0, 20)); // 1회: MISS → DB

            // 이벤트 등록 상황 시뮬레이션 — 캐시 직접 무효화
            var cache = cacheManager.getCache("event-search");
            if (cache != null) cache.clear();

            searchService.searchEventsV2(request, PageRequest.of(0, 20)); // 2회: MISS → DB

            // then — 총 2회 호출
            verify(eventSearchRepository, times(2))
                    .searchEvents(any(SearchRequestDto.class), any(Pageable.class));
        }
    }

    // ═══════════════════════════════════════════════════════
    // SRCH-S-11 ~ 12 : Redis Cache-Aside (도전 기능)
    // ═══════════════════════════════════════════════════════

    /*
     * SRCH-S-11, 12 — Redis Cache-Aside 도전 기능 구현 후 활성화
     *
     * SA 문서 ADR-006: cache.provider=redis 전환 시 RedisCacheManager 자동 적용
     * → application-test-redis.yml 생성 후 @ActiveProfiles("test-redis")로 전환
     *
     * SRCH-S-11: Cache-Aside MISS → DB 조회 → Redis SET 호출 확인
     * SRCH-S-12: Cache-Aside HIT → Repository 미호출 확인
     *
     * 구현 완료 후 아래 주석을 해제하세요:
     *
     * @Test
     * @DisplayName("SRCH-S-11: Cache-Aside MISS 시 DB 조회 후 캐시에 저장된다")
     * void searchEventsV2_cacheMiss_dbQueriedAndCached() {
     *     // Cache-Aside MISS 시나리오
     *     // RedisCacheManager 방식이므로 @Cacheable 동작으로 검증
     *     // → 동일 조건 2회 호출 시 Repository 1회 호출 (SRCH-S-09와 동일 패턴)
     * }
     *
     * @Test
     * @DisplayName("SRCH-S-12: Cache-Aside HIT 시 Repository가 호출되지 않는다")
     * void searchEventsV2_cacheHit_repositoryNotCalled() {
     *     // 첫 번째 호출 후 두 번째 호출 시 Repository 미호출 검증
     * }
     */

    // ═══════════════════════════════════════════════════════
    // SRCH-S-13 ~ 15 : 인기 검색어 ZSet 조회
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("SRCH-S-13 ~ 15 : 인기 검색어 ZSet 조회")
    class PopularKeywordTests {

        /**
         * SRCH-S-13
         * ZSet 정상 응답 — rank/keyword/score 구조 + 내림차순 정렬 검증
         * SA 문서 FN-SRCH-04: ZREVRANGE search-keywords 0 9 WITHSCORES
         * 응답: [{ rank, keyword, score }] 내림차순 정렬
         */
        @Test
        @DisplayName("SRCH-S-13: ZSet 정상 응답 시 rank/keyword/score 구조로 내림차순 정렬 반환")
        void getPopularKeywords_normalCase_returnsRankedList() {
            // given — ZREVRANGEWITHSCORES Mock 데이터 (내림차순)
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(tuple("세븐틴", 1420.0));
            tuples.add(tuple("오페라의 유령", 987.0));
            tuples.add(tuple("KBO 올스타전", 764.0));

            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(eq(POPULAR_KEY), eq(0L), eq(9L)))
                    .willReturn(tuples);

            // when
            List<PopularKeywordResponseDto> result = searchService.getPopularKeywords();

            // then
            assertThat(result).hasSize(3);

            // rank 1위 검증
            assertThat(result.get(0).getRank()).isEqualTo(1);
            assertThat(result.get(0).getKeyword()).isEqualTo("세븐틴");
            assertThat(result.get(0).getScore()).isEqualTo(1420.0);

            // rank 2위 검증
            assertThat(result.get(1).getRank()).isEqualTo(2);
            assertThat(result.get(1).getKeyword()).isEqualTo("오페라의 유령");

            // rank 3위 검증
            assertThat(result.get(2).getRank()).isEqualTo(3);
            assertThat(result.get(2).getKeyword()).isEqualTo("KBO 올스타전");

            // 내림차순 정렬 확인 (앞 순위가 더 높은 score)
            assertThat(result.get(0).getScore())
                    .isGreaterThan(result.get(1).getScore());
            assertThat(result.get(1).getScore())
                    .isGreaterThan(result.get(2).getScore());
        }

        /**
         * SRCH-S-14
         * ZSet 비어있을 때 빈 배열 반환
         * SA 문서 FN-SRCH-04: 데이터 없음 → 200 OK + 빈 배열
         */
        @Test
        @DisplayName("SRCH-S-14: ZSet 비어있을 때 빈 리스트가 반환된다")
        void getPopularKeywords_emptyZSet_returnsEmptyList() {
            // given
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(eq(POPULAR_KEY), eq(0L), eq(9L)))
                    .willReturn(new LinkedHashSet<>());

            // when
            List<PopularKeywordResponseDto> result = searchService.getPopularKeywords();

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }

        /**
         * SRCH-S-15
         * Redis 장애 시 Graceful Degradation
         * SA 문서 FN-SRCH-04: Redis 장애 → 200 OK + 빈 배열 반환
         * 검색 기능(v1, v2)에 영향 없어야 함
         *
         * SearchService.getPopularKeywords() 내부에 try-catch가 있어야 통과
         */
        @Test
        @DisplayName("SRCH-S-15: Redis 장애 시 예외를 catch하고 빈 배열을 반환한다")
        void getPopularKeywords_redisException_returnsEmptyListGracefully() {
            // given — Redis 연결 실패 시뮬레이션
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .willThrow(new org.springframework.data.redis.RedisConnectionFailureException(
                            "Redis connection refused"));

            // when — 예외가 서비스 밖으로 전파되지 않아야 함
            List<PopularKeywordResponseDto> result = searchService.getPopularKeywords();

            // then — 빈 배열 반환, 예외 미전파
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════
    // SRCH-S-16 : 페이징 파라미터 검증
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("SRCH-S-16 : 페이징 파라미터 동작 검증")
    class PagingTests {

        /**
         * SRCH-S-16
         * size/page 파라미터 정상 동작
         * Page 객체 totalPages/content 크기 검증
         *
         * 시나리오: 전체 25개 중 page=1, size=10 요청
         * 기대: content 10개, totalPages 3, totalElements 25
         */
        @Test
        @DisplayName("SRCH-S-16: size/page 파라미터가 Page 객체에 정확히 반영된다")
        void searchEventsV1_pagingParams_correctPageObjectReturned() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("뮤지컬")
                    .build();

            // 2페이지(0-indexed 1) 데이터: 10개 (전체 25개 중)
            List<Event> pageContent = buildEventList(10);
            Page<Event> mockPage = new PageImpl<>(
                    pageContent,
                    PageRequest.of(1, 10),
                    25L
            );

            given(eventSearchRepository.searchEvents(
                    any(SearchRequestDto.class), any(Pageable.class)))
                    .willReturn(mockPage);

            // when
            Page<EventSummaryResponseDto> result =
                    searchService.searchEventsV1(request, PageRequest.of(1, 10));

            // then
            assertThat(result.getContent()).hasSize(10);
            assertThat(result.getTotalElements()).isEqualTo(25);
            assertThat(result.getTotalPages()).isEqualTo(3);   // ceil(25/10) = 3
            assertThat(result.getNumber()).isEqualTo(1);       // 현재 페이지 (0-indexed)
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.isFirst()).isFalse();
            assertThat(result.isLast()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════
    // SRCH-S-17 : 정렬 검증
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("SRCH-S-17 : 정렬 파라미터 동작 검증")
    class SortingTests {

        /**
         * SRCH-S-17-A
         * eventDate,asc 정렬 — 반환 목록이 날짜 오름차순인지 검증
         */
        @Test
        @DisplayName("SRCH-S-17-A: eventDate,asc 정렬 시 날짜 오름차순으로 반환된다")
        void searchEventsV1_sortByEventDateAsc_returnsDateAscendingOrder() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("콘서트")
                    .build();

            List<Event> sortedByDate = List.of(
                    eventWithDate("콘서트 1회", LocalDateTime.of(2026, 5, 1, 19, 0), 50000),
                    eventWithDate("콘서트 2회", LocalDateTime.of(2026, 5, 8, 19, 0), 50000),
                    eventWithDate("콘서트 3회", LocalDateTime.of(2026, 5, 15, 19, 0), 50000)
            );

            Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "eventDate"));
            given(eventSearchRepository.searchEvents(any(SearchRequestDto.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(sortedByDate, pageable, 3));

            // when
            Page<EventSummaryResponseDto> result =
                    searchService.searchEventsV1(request, pageable);

            // then — 날짜 오름차순 검증
            List<EventSummaryResponseDto> content = result.getContent();
            assertThat(content).hasSize(3);
            for (int i = 0; i < content.size() - 1; i++) {
                assertThat(content.get(i).getEventDate())
                        .isBefore(content.get(i + 1).getEventDate());
            }
        }

        /**
         * SRCH-S-17-B
         * price,asc 정렬 — 반환 목록이 minPrice 오름차순인지 검증
         */
        @Test
        @DisplayName("SRCH-S-17-B: price,asc 정렬 시 가격 오름차순으로 반환된다")
        void searchEventsV1_sortByPriceAsc_returnsPriceAscendingOrder() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("콘서트")
                    .build();

            List<Event> sortedByPrice = List.of(
                    eventWithDate("콘서트 A", LocalDateTime.of(2026, 5, 1, 19, 0), 30000),
                    eventWithDate("콘서트 B", LocalDateTime.of(2026, 5, 1, 19, 0), 55000),
                    eventWithDate("콘서트 C", LocalDateTime.of(2026, 5, 1, 19, 0), 110000)
            );

            Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "price"));
            given(eventSearchRepository.searchEvents(any(SearchRequestDto.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(sortedByPrice, pageable, 3));

            // when
            Page<EventSummaryResponseDto> result =
                    searchService.searchEventsV1(request, pageable);

            // then — 가격 오름차순 검증
            List<EventSummaryResponseDto> content = result.getContent();
            assertThat(content).hasSize(3);
            for (int i = 0; i < content.size() - 1; i++) {
                assertThat(content.get(i).getMinPrice())
                        .isLessThanOrEqualTo(content.get(i + 1).getMinPrice());
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 헬퍼 메서드
    // ═══════════════════════════════════════════════════════

    /** ZSet TypedTuple 생성 헬퍼 */
    private ZSetOperations.TypedTuple<String> tuple(String value, double score) {
        return new ZSetOperations.TypedTuple<>() {
            @Override public String getValue() { return value; }
            @Override public Double getScore() { return score; }
            @Override public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return Double.compare(o.getScore(), score);
            }
        };
    }

    /** 페이징 테스트용 이벤트 리스트 생성 */
    private List<Event> buildEventList(int count) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(eventWithDate(
                    "이벤트 " + i,
                    LocalDateTime.of(2026, 5, 1 + (i % 28), 19, 0),
                    30000 + (i * 1000)
            ));
        }
        return events;
    }
}
