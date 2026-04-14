package com.example.ticket_javara.domain.search.dto.request;

import com.example.ticket_javara.domain.event.entity.EventCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequestDto {
    private String keyword;
    private EventCategory category;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer minPrice;
    private Integer maxPrice;
}
