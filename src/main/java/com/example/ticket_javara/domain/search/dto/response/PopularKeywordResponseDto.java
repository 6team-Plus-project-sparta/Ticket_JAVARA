package com.example.ticket_javara.domain.search.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PopularKeywordResponseDto {
    private Integer rank;
    private String keyword;
    private Double score;
}
