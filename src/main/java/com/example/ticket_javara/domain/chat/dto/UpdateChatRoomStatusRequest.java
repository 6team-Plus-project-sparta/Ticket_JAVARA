package com.example.ticket_javara.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅방 상태 전이 요청 DTO
 * Map<String, String> body 방식 대신 명확한 타입 + Bean Validation 적용
 */
@Getter
@NoArgsConstructor
public class UpdateChatRoomStatusRequest {

    @NotBlank(message = "status는 필수입니다.")
    private String status;
}
