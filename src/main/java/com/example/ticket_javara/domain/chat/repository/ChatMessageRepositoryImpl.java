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

    private BooleanExpression cursorCondition(Long cursor, QChatMessage chatMessage) {
        if (cursor == null) {
            return null;
        }
        return chatMessage.chatMessageId.lt(cursor);
    }
}
