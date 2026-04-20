package com.example.ticket_javara.domain.search.service;

import com.example.ticket_javara.domain.event.entity.*;
import com.example.ticket_javara.domain.event.repository.SeatRepository;
import com.example.ticket_javara.domain.search.dto.request.SearchRequestDto;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.example.ticket_javara.domain.search.repository.EventSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * SearchService v1 단위 테스트 — SRCH-S-01 ~ 08
 *
 * 테스트 전략:
 * - Spring 컨텍스트 없이 @ExtendWith(MockitoExtension.class)로 순수 단위 테스트 실행
 * - 외부 의존성(DB, Redis)은 전부 Mock 처리 → 빠른 실행 속도 보장
 *
 * 의존성 구조:
 * - EventSearchRepository : QueryDSL 동적 검색 쿼리 (BooleanBuilder)
 * - SeatRepository         : mapToSummary() 내부에서 잔여 좌석 수 조회
 * - RedisTemplate          : evictSearchCache()에서 SCAN + DEL 사용
 * - StringRedisTemplate    : incrementSearchKeyword()에서 ZINCRBY 사용
 * - CacheManager           : isCacheHit() 캐시 히트 여부 확인
 *
 * SRCH-S-07, 08 설계 결정:
 * - SA 문서(FN-SRCH-01) 기준: 검색 API 호출 시 서비스 내부에서 ZINCRBY 함께 처리
 * - searchEventsV1() 내부에서 incrementSearchKeyword()를 직접 호출하는 구조
 * - 따라서 searchEventsV1() 호출만으로 ZINCRBY 동작을 함께 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SearchService v1 단위 테스트 — SRCH-S-01 ~ 08")
class SearchServiceV1Test {

    @Mock
    private EventSearchRepository eventSearchRepository;

    @Mock
    private SeatRepository seatRepository;

    // evictSearchCache()의 SCAN + DEL에서 사용
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    // incrementSearchKeyword()의 ZINCRBY에서 사용
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    // isCacheHit() 캐시 히트 여부 확인에서 사용
    @Mock
    private CacheManager cacheManager;

    // stringRedisTemplate.opsForZSet() 반환값 Mock
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private SearchService searchService;

    private static final String POPULAR_KEY = "search-keywords";

    /**
     * 각 테스트 전 ZSetOperations Mock 연결
     * lenient() 사용 이유:
     * - SRCH-S-01~06은 ZSet을 사용하지 않아 이 스텁이 호출되지 않음
     * - Mockito 기본 설정은 사용되지 않은 스텁을 경고로 처리하므로
     *   lenient()로 불필요한 경고 억제
     */
    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    // ───────────────────────────────────────────────────────
    // 공통 픽스처
    // ───────────────────────────────────────────────────────

    /**
     * 테스트용 Event 샘플 객체 생성
     * mapToSummary()에서 아래 필드를 사용하므로 전부 세팅:
     * - venue.getName()    → venueName
     * - sections (price)   → minPrice 계산
     * - seatRepository     → remainingSeats (별도 Mock 스텁 필요)
     */
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

        event.getSections().add(section);  // sections는 별도로 추가
        return event;
    }

    /**
     * 테스트용 Page<Event> 샘플 생성
     * PageImpl 기본 생성자 사용 — totalElements = content.size()
     */
    private Page<Event> samplePage() {
        return new PageImpl<>(List.of(sampleEvent()));
    }

    // ═══════════════════════════════════════════════════════
    // SRCH-S-01 ~ 06: 검색 조건 및 결과 검증
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("SRCH-S-01 ~ 06: 검색 조건 및 결과 검증")
    class SearchConditionTests {

        /**
         * SRCH-S-01
         * keyword만 입력 시 Repository에 keyword 조건이 전달되는지 확인
         * 실제 LIKE 쿼리 생성은 QueryDSL Repository 레이어에서 발생하므로
         * 여기서는 SearchRequestDto에 keyword가 정확히 담겨 전달되는지만 검증
         *
         * ArgumentCaptor 사용 이유:
         * Repository에 실제로 전달된 DTO를 캡처해서 내부 필드값을 직접 검증하기 위함
         * eq() matcher로는 DTO 내부 필드를 개별 검증하기 어려움
         */
        @Test
        @DisplayName("SRCH-S-01: keyword만 입력 시 keyword 조건이 Repository에 전달된다")
        void searchV1_keywordOnly_passesKeywordToRepository() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("세븐틴")
                    .build();

            ArgumentCaptor<SearchRequestDto> captor = ArgumentCaptor.forClass(SearchRequestDto.class);
            given(eventSearchRepository.searchEvents(captor.capture(), any(Pageable.class)))
                    .willReturn(samplePage());
            // mapToSummary() 내부에서 seatRepository 호출 — NPE 방지용 스텁
            given(seatRepository.countAvailableSeatsByEventId(any())).willReturn(200L);

            // when
            searchService.searchEventsV1(request, PageRequest.of(0, 20));

            // then
            assertThat(captor.getValue().getKeyword()).isEqualTo("세븐틴");
            assertThat(captor.getValue().getCategory()).isNull();
        }

        /**
         * SRCH-S-02
         * category 필터 단독 적용 시 category 조건이 Repository에 전달되는지 확인
         * keyword는 null이어야 함 — BooleanBuilder에서 keyword 조건이 추가되지 않아야 함
         */
        @Test
        @DisplayName("SRCH-S-02: category 필터 단독 입력 시 category 조건이 Repository에 전달된다")
        void searchV1_categoryOnly_passesCategoryToRepository() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .category(EventCategory.CONCERT)
                    .build();

            ArgumentCaptor<SearchRequestDto> captor = ArgumentCaptor.forClass(SearchRequestDto.class);
            given(eventSearchRepository.searchEvents(captor.capture(), any(Pageable.class)))
                    .willReturn(samplePage());
            given(seatRepository.countAvailableSeatsByEventId(any())).willReturn(200L);

            // when
            searchService.searchEventsV1(request, PageRequest.of(0, 20));

            // then
            assertThat(captor.getValue().getCategory()).isEqualTo(EventCategory.CONCERT);
            assertThat(captor.getValue().getKeyword()).isNull();
        }

        /**
         * SRCH-S-03
         * startDate / endDate 범위 필터
         * Repository 구현체에서 아래와 같이 변환됨:
         * - startDate → event.eventDate.goe(startDate.atStartOfDay())
         * - endDate   → event.eventDate.loe(endDate.atTime(LocalTime.MAX))
         */
        @Test
        @DisplayName("SRCH-S-03: startDate/endDate 범위 필터가 Repository에 전달된다")
        void searchV1_dateRange_passesDateConditionToRepository() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .startDate(LocalDate.of(2026, 5, 1))
                    .endDate(LocalDate.of(2026, 5, 31))
                    .build();

            ArgumentCaptor<SearchRequestDto> captor = ArgumentCaptor.forClass(SearchRequestDto.class);
            given(eventSearchRepository.searchEvents(captor.capture(), any(Pageable.class)))
                    .willReturn(samplePage());
            given(seatRepository.countAvailableSeatsByEventId(any())).willReturn(200L);

            // when
            searchService.searchEventsV1(request, PageRequest.of(0, 20));

            // then
            assertThat(captor.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(captor.getValue().getEndDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        }

        /**
         * SRCH-S-04
         * minPrice / maxPrice 가격 필터
         * Repository 구현체에서 EXISTS 서브쿼리로 변환됨 (ADR-009):
         * EXISTS (SELECT 1 FROM section s WHERE s.event_id = e.id
         *         AND s.price >= minPrice AND s.price <= maxPrice)
         * "구역 중 하나라도 해당 가격 범위에 속하면 이벤트 노출" 정책
         */
        @Test
        @DisplayName("SRCH-S-04: minPrice/maxPrice 가격 필터가 Repository에 전달된다")
        void searchV1_priceRange_passesPriceConditionToRepository() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .minPrice(30000)
                    .maxPrice(100000)
                    .build();

            ArgumentCaptor<SearchRequestDto> captor = ArgumentCaptor.forClass(SearchRequestDto.class);
            given(eventSearchRepository.searchEvents(captor.capture(), any(Pageable.class)))
                    .willReturn(samplePage());
            given(seatRepository.countAvailableSeatsByEventId(any())).willReturn(200L);

            // when
            searchService.searchEventsV1(request, PageRequest.of(0, 20));

            // then
            assertThat(captor.getValue().getMinPrice()).isEqualTo(30000);
            assertThat(captor.getValue().getMaxPrice()).isEqualTo(100000);
        }

        /**
         * SRCH-S-05
         * 다중 조건 조합 — BooleanBuilder가 모든 조건을 AND로 연결하는지 검증
         * keyword + category + startDate + endDate + minPrice + maxPrice 전부 입력
         */
        @Test
        @DisplayName("SRCH-S-05: 다중 조건 조합 시 모든 조건이 Repository에 전달된다")
        void searchV1_multipleConditions_allConditionsPassedToRepository() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("뮤지컬")
                    .category(EventCategory.MUSICAL)
                    .startDate(LocalDate.of(2026, 5, 1))
                    .endDate(LocalDate.of(2026, 5, 31))
                    .minPrice(30000)
                    .maxPrice(150000)
                    .build();

            ArgumentCaptor<SearchRequestDto> captor = ArgumentCaptor.forClass(SearchRequestDto.class);
            given(eventSearchRepository.searchEvents(captor.capture(), any(Pageable.class)))
                    .willReturn(samplePage());
            given(seatRepository.countAvailableSeatsByEventId(any())).willReturn(200L);

            // when
            searchService.searchEventsV1(request, PageRequest.of(0, 20));

            // then — 조건 누락 없이 전부 전달됐는지 검증
            SearchRequestDto captured = captor.getValue();
            assertThat(captured.getKeyword()).isEqualTo("뮤지컬");
            assertThat(captured.getCategory()).isEqualTo(EventCategory.MUSICAL);
            assertThat(captured.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(captured.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 31));
            assertThat(captured.getMinPrice()).isEqualTo(30000);
            assertThat(captured.getMaxPrice()).isEqualTo(150000);
        }

        /**
         * SRCH-S-06
         * 검색 결과가 없을 때 빈 Page 반환 검증
         * - HTTP 200 OK (예외 없이 정상 반환)
         * - content 비어있음
         * - totalElements = 0
         *
         * seatRepository 스텁이 필요 없는 이유:
         * events가 비어있으면 mapToSummary()의 map() 람다가 실행되지 않음
         */
        @Test
        @DisplayName("SRCH-S-06: 검색 결과 없을 때 빈 Page가 반환된다")
        void searchV1_noResults_returnsEmptyPage() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("없는공연XXXXX")
                    .build();

            given(eventSearchRepository.searchEvents(any(SearchRequestDto.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(Collections.emptyList()));

            // when
            Page<EventSummaryResponseDto> result =
                    searchService.searchEventsV1(request, PageRequest.of(0, 20));

            // then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════
    // SRCH-S-07 ~ 08: Redis ZINCRBY 인기 검색어 집계 검증
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("SRCH-S-07 ~ 08: Redis ZINCRBY 인기 검색어 집계 검증")
    class RedisZincrbyTests {

        /**
         * SRCH-S-07
         * searchEventsV1() 호출 시 내부에서 incrementSearchKeyword()가 함께 실행됨
         * SA 문서(FN-SRCH-01) 처리 로직 1번 기준:
         * 검색어가 존재하면 Redis ZSet ZINCRBY search-keywords {keyword} 1
         */
        @Test
        @DisplayName("SRCH-S-07: keyword 입력 시 Redis ZINCRBY가 1회 호출된다")
        void incrementSearchKeyword_withKeyword_zincrbyCalledOnce() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("세븐틴")
                    .build();

            given(eventSearchRepository.searchEvents(any(), any())).willReturn(samplePage());
            given(seatRepository.countAvailableSeatsByEventId(any())).willReturn(200L);

            // when — searchEventsV1() 내부에서 ZINCRBY가 함께 실행됨
            searchService.searchEventsV1(request, PageRequest.of(0, 20));

            // then
            verify(zSetOperations, times(1))
                    .incrementScore(eq(POPULAR_KEY), eq("세븐틴"), eq(1.0));
        }

        /**
         * SRCH-S-08
         * keyword가 null이면 Redis ZINCRBY 미호출
         * incrementSearchKeyword() 내부 null 체크 동작 검증
         */
        @Test
        @DisplayName("SRCH-S-08: keyword가 null이면 Redis ZINCRBY가 호출되지 않는다")
        void incrementSearchKeyword_withNullKeyword_zincrbyNotCalled() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .build();  // keyword null

            given(eventSearchRepository.searchEvents(any(), any())).willReturn(samplePage());
            given(seatRepository.countAvailableSeatsByEventId(any())).willReturn(200L);

            // when
            searchService.searchEventsV1(request, PageRequest.of(0, 20));

            // then
            verify(zSetOperations, never())
                    .incrementScore(anyString(), anyString(), anyDouble());
        }

        /**
         * SRCH-S-08 확장
         * keyword가 공백 문자열(" ")이면 Redis ZINCRBY 미호출
         * incrementSearchKeyword() 내부 trim().isEmpty() 체크 동작 검증
         */
        @Test
        @DisplayName("SRCH-S-08 확장: keyword가 빈 문자열이면 Redis ZINCRBY가 호출되지 않는다")
        void incrementSearchKeyword_withBlankKeyword_zincrbyNotCalled() {
            // given
            SearchRequestDto request = SearchRequestDto.builder()
                    .keyword("   ")  // 공백 keyword
                    .build();

            given(eventSearchRepository.searchEvents(any(), any())).willReturn(samplePage());
            given(seatRepository.countAvailableSeatsByEventId(any())).willReturn(200L);

            // when
            searchService.searchEventsV1(request, PageRequest.of(0, 20));

            // then
            verify(zSetOperations, never())
                    .incrementScore(anyString(), anyString(), anyDouble());
        }
    }
}