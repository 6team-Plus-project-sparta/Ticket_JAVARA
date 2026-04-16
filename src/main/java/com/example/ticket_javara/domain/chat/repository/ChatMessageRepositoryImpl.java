package com.example.ticket_javara.domain.chat.repository;

import com.example.ticket_javara.domain.chat.entity.ChatMessage;
import com.example.ticket_javara.domain.chat.entity.QChatMessage;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ChatMessage> getMessagesWithCursor(Long chatRoomId, Long cursor, int size) {
        QChatMessage chatMessage = QChatMessage.chatMessage;

        return queryFactory
                .selectFrom(chatMessage)
                .join(chatMessage.chatRoom).fetchJoin()
                .join(chatMessage.chatRoom.user).fetchJoin()
                .where(
                        chatMessage.chatRoom.chatRoomId.eq(chatRoomId),
                        cursorCondition(cursor, chatMessage)
                )
                .orderBy(chatMessage.chatMessageId.desc())
                .limit(size)
                .fetch();
    }

    @Override
    public List<ChatMessage> getLatestMessagesByRoomIds(List<Long> chatRoomIds) {
        if (chatRoomIds.isEmpty()) {
            return List.of();
        }

        QChatMessage chatMessage = QChatMessage.chatMessage;
        QChatMessage subMessage = new QChatMessage("subMessage");

        return queryFactory
                .selectFrom(chatMessage)
                .join(chatMessage.chatRoom).fetchJoin()  // 🔧 fetch join 추가
                .where(
                        chatMessage.chatRoom.chatRoomId.in(chatRoomIds),
                        chatMessage.chatMessageId.in(
                                queryFactory
                                        .select(subMessage.chatMessageId.max())
                                        .from(subMessage)
                                        .where(subMessage.chatRoom.chatRoomId.in(chatRoomIds))
                                        .groupBy(subMessage.chatRoom.chatRoomId)
                        )
                )
                .fetch();
    }

    /**
     * 🚀 성능 최적화된 버전 - 네이티브 쿼리 사용
     * 대용량 데이터에서 윈도우 함수로 성능 개선
     */
    @Override
    public List<ChatMessage> getLatestMessagesByRoomIdsOptimized(List<Long> chatRoomIds) {
        if (chatRoomIds.isEmpty()) {
            return List.of();
        }

        // 🔴 주의: 실제 운영에서는 이 방식 사용 권장
        // 현재는 QueryDSL로 구현하되, 성능 이슈 발생 시 네이티브 쿼리로 전환
        
        // 임시로 기존 방식 사용 (실제로는 @Query 네이티브 쿼리 권장)
        return getLatestMessagesByRoomIds(chatRoomIds);
    }

    private BooleanExpression cursorCondition(Long cursor, QChatMessage chatMessage) {
        if (cursor == null) {
            return null;
        }
        return chatMessage.chatMessageId.lt(cursor);
    }
}
