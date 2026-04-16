package com.example.ticket_javara.domain.event.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionCreateDto {

    @NotBlank(message = "구역 이름은 필수입니다.")
    private String sectionName;

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
    private Integer price;

    @NotNull(message = "행 수는 필수입니다.")
    @Min(value = 1, message = "행 개수는 1 이상이어야 합니다.")
    private Integer rowCount;

    @NotNull(message = "열 수는 필수입니다.")
    @Min(value = 1, message = "열 개수는 1 이상이어야 합니다.")
    private Integer colCount;
}
