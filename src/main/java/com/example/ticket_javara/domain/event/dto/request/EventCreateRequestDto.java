package com.example.ticket_javara.domain.event.dto.request;

import com.example.ticket_javara.domain.event.entity.EventCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class EventCreateRequestDto {

    @NotBlank(message = "이벤트 제목은 필수입니다.")
    private String title;

    @NotNull(message = "카테고리는 필수입니다.")
    private EventCategory category;

    @NotNull(message = "공연장 ID는 필수입니다.")
    private Long venueId;

    @NotNull(message = "이벤트 일시는 필수입니다.")
    private LocalDateTime eventDate;

    @NotNull(message = "판매 시작일시는 필수입니다.")
    private LocalDateTime saleStartAt;

    @NotNull(message = "판매 종료일시는 필수입니다.")
    private LocalDateTime saleEndAt;

    @NotNull(message = "회차 번호는 필수입니다.")
    private Integer roundNumber;

    private String description;

    private String thumbnailUrl;

    @Valid
    @NotEmpty(message = "구역 정보는 최소 1개 이상 필요합니다.")
    private List<SectionCreateDto> sections;
}
