package com.example.ticket_javara.domain.chat.repository;

import com.example.ticket_javara.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long>, ChatMessageRepositoryCustom {
    
    // TODO: QueryDSL을 사용하여 무한 스크롤(커서 기반) 페이징 조회 구현 권장
    // 커서(cursor)인 chat_message_id 미만인 데이터를 chatRoomId 기준으로 가져오게 됨
}
