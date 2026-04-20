package com.example.ticket_javara.domain.chat.dto;

import com.example.ticket_javara.domain.chat.entity.ChatRoomStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅방 상태 전이 요청 DTO
 * String 대신 Enum(ChatRoomStatus)을 사용하여 Jackson 역직렬화 단계에서
 * 유효하지 않은 문자열 입력을 원천 차단.
 */
@Getter
@NoArgsConstructor
public class UpdateChatRoomStatusRequest {

    @NotNull(message = "status는 필수입니다.")
    private ChatRoomStatus status;
}
