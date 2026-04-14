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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<Page<EventSummaryResponseDto>> searchEventsV1(
            @ModelAttribute SearchRequestDto requestDto,
            @PageableDefault(sort = "eventDate", direction = Sort.Direction.ASC) Pageable pageable) {
        
        Page<EventSummaryResponseDto> result = searchService.searchEventsV1(requestDto, pageable);
        return ResponseEntity.ok(result);
    }
}
