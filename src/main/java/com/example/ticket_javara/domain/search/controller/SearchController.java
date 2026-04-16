package com.example.ticket_javara.domain.search.controller;

import com.example.ticket_javara.domain.search.dto.request.SearchRequestDto;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.example.ticket_javara.domain.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticket_javara.domain.search.dto.response.PopularKeywordResponseDto;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/v1/events/search")
    public ResponseEntity<Page<EventSummaryResponseDto>> searchEventsV1(
            @ModelAttribute SearchRequestDto requestDto,
            @PageableDefault(sort = "eventDate", direction = Sort.Direction.ASC) Pageable pageable) {
        
        Page<EventSummaryResponseDto> result = searchService.searchEventsV1(requestDto, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/v2/events/search")
    public ResponseEntity<Page<EventSummaryResponseDto>> searchEventsV2(
            @ModelAttribute SearchRequestDto requestDto,
            @PageableDefault(sort = "eventDate", direction = Sort.Direction.ASC) Pageable pageable,
            HttpServletResponse response) {
        
        // @Cacheable HIT 시 메서드 본문이 실행 안 되므로
        // 인기 검색어 집계는 캐시 호출 바깥(컨트롤러)에서 처리
        if (requestDto.getKeyword() != null && !requestDto.getKeyword().isBlank()) {
            searchService.incrementSearchKeyword(requestDto.getKeyword());
        }
        
        // 캐시 조회 전에 HIT 여부 판단 후 헤더 세팅
        boolean isCacheHit = searchService.isCacheHit(requestDto, pageable);
        response.setHeader("X-Cache", isCacheHit ? "HIT" : "MISS");
        
        Page<EventSummaryResponseDto> result = searchService.searchEventsV2(requestDto, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search/popular")
    public ResponseEntity<List<PopularKeywordResponseDto>> getPopularKeywords() {
        List<PopularKeywordResponseDto> result = searchService.getPopularKeywords();
        return ResponseEntity.ok(result);
    }
}
