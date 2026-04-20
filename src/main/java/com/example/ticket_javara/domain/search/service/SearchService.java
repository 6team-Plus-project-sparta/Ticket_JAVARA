package com.example.ticket_javara.domain.search.service;

import com.example.ticket_javara.domain.event.entity.Event;
import com.example.ticket_javara.domain.event.repository.SeatRepository;
import com.example.ticket_javara.domain.search.dto.request.SearchRequestDto;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.example.ticket_javara.domain.search.dto.response.PopularKeywordResponseDto;
import com.example.ticket_javara.domain.search.dto.response.RestPageImpl;
import com.example.ticket_javara.domain.search.repository.EventSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final EventSearchRepository eventSearchRepository;
    private final SeatRepository seatRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheManager cacheManager;

    private static final String POPULAR_KEYWORD_KEY = "search-keywords";

    public Page<EventSummaryResponseDto> searchEventsV1(SearchRequestDto requestDto, Pageable pageable) {
        long startTime = System.currentTimeMillis();

        Page<Event> events = eventSearchRepository.searchEvents(requestDto, pageable);

        // 검색 결과가 존재할 때만 인기 검색어로 기록 (선택적 비즈니스 룰 적용)
        if (events.hasContent() && requestDto.getKeyword() != null) {
            incrementSearchKeyword(requestDto.getKeyword());
        }

        Page<EventSummaryResponseDto> result = mapToSummary(events);

        long endTime = System.currentTimeMillis();
        // v1검색 소요시간 체크
        log.info("[V1 Search] query execution time: {} ms", (endTime - startTime));

        return result;
    }

    @org.springframework.cache.annotation.Cacheable(value = "event-search", keyGenerator = "eventSearchKeyGenerator")
    public Page<EventSummaryResponseDto> searchEventsV2(SearchRequestDto requestDto, Pageable pageable) {
        long startTime = System.currentTimeMillis();

        Page<Event> events = eventSearchRepository.searchEvents(requestDto, pageable);

        long endTime = System.currentTimeMillis();
        // v2검색 소요시간 체크
        log.info("[V2 Search] query execution time: {} ms", (endTime - startTime));

        Page<EventSummaryResponseDto> result = mapToSummary(events);
        return new RestPageImpl<>(result.getContent(), result.getPageable(), result.getTotalElements());
    }

    public List<PopularKeywordResponseDto> getPopularKeywords() {
        try {
            Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(POPULAR_KEYWORD_KEY, 0, 9);
            List<PopularKeywordResponseDto> result = new ArrayList<>();

            if (typedTuples != null) {
                int rank = 1;
                for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                    result.add(PopularKeywordResponseDto.builder()
                            .rank(rank++)
                            .keyword(tuple.getValue())
                            .score(tuple.getScore())
                            .build());
                }
            }
            return result;

        } catch (Exception e) {
            // SA 문서 FN-SRCH-04: Redis 장애 시 Graceful Degradation
            // 빈 배열 반환 — 검색 기능에 영향 없음
            log.warn("[SearchService] 인기 검색어 조회 실패 — 빈 배열 반환: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Page<EventSummaryResponseDto> mapToSummary(Page<Event> events) {
        return events.map(event -> {
            Integer minPrice = event.getSections().stream()
                    .mapToInt(section -> section.getPrice())
                    .min()
                    .orElse(0);

            long remainingSeats = seatRepository.countAvailableSeatsByEventId(event.getEventId());

            return EventSummaryResponseDto.builder()
                    .eventId(event.getEventId())
                    .title(event.getTitle())
                    .category(event.getCategory())
                    .venueName(event.getVenue().getName())
                    .eventDate(event.getEventDate())
                    .minPrice(minPrice)
                    .remainingSeats(remainingSeats)
                    .thumbnailUrl(event.getThumbnailUrl())
                    .build();
        });
    }

    /**
     * 이벤트 등록 시 호출 — event-search::* 패턴 Redis 키 전체 삭제
     * KEYS 명령 사용 금지 → SCAN + DEL 사용
     */
    public void evictSearchCache() {
        ScanOptions options = ScanOptions.scanOptions()
            .match("event-search::*")
            .count(100)
            .build();

        RedisConnection connection = Objects.requireNonNull(
            redisTemplate.getConnectionFactory()).getConnection();

        try (Cursor<byte[]> cursor = connection.scan(options)) {
            while (cursor.hasNext()) {
                String key = new String(cursor.next(), StandardCharsets.UTF_8);
                redisTemplate.delete(key);
            }
            log.info("[SearchService] event-search::* 캐시 전체 삭제 완료");
        } catch (Exception e) {
            log.warn("[SearchService] event-search:: 캐시 삭제 실패 — 비즈니스 로직에 영향 없음: {}", e.getMessage());
        }
    }

    /**
     * 검색어 캐시 점수 누적 (ZINCRBY)
     */
    public void incrementSearchKeyword(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            try {
                stringRedisTemplate.opsForZSet()
                        .incrementScore(POPULAR_KEYWORD_KEY, keyword.trim(), 1.0);
            } catch (Exception e) {
                log.warn("[SearchService] 검색어 카운트 증가 실패: {}", e.getMessage());
            }
        }
    }

    public boolean isCacheHit(SearchRequestDto requestDto, Pageable pageable) {
        Cache cache = cacheManager.getCache("event-search");
        if (cache == null) return false;

        String cacheKey = String.format("%s:%s:%s:%s:%s:%s:%d:%d:%s",
                requestDto.getKeyword(),
                requestDto.getCategory(),
                requestDto.getStartDate(),
                requestDto.getEndDate(),
                requestDto.getMinPrice(),
                requestDto.getMaxPrice(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().toString());

        return cache.get(cacheKey) != null;
    }
}
