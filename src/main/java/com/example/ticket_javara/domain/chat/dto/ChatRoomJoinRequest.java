package com.example.ticket_javara.domain.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅방 입장(JOIN) STOMP 메시지 페이로드 DTO
 * Map<String, Long> 사용 시 발생하는 역직렬화 ClassCastException 방지
 */
@Getter
@NoArgsConstructor
public class ChatRoomJoinRequest {

    @NotNull(message = "chatRoomId는 필수입니다.")
    private Long chatRoomId;
}
