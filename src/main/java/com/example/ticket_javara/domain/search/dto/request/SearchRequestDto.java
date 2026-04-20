package com.example.ticket_javara.domain.search.dto.request;

import com.example.ticket_javara.domain.event.entity.EventCategory;
import lombok.*;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SearchRequestDto {
    private String keyword;
    private EventCategory category;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer minPrice;
    private Integer maxPrice;
}
