package com.example.ticket_javara.domain.event.dto.request;

import com.example.ticket_javara.domain.event.entity.EventStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventStatusUpdateRequestDto {

    @NotNull(message = "변경할 상태값은 필수입니다.")
    private EventStatus status;
}