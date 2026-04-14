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
                .where(
                        chatMessage.chatRoom.chatRoomId.eq(chatRoomId),
                        cursorCondition(cursor, chatMessage)
                )
                .orderBy(chatMessage.chatMessageId.desc())
                .limit(size)
                .fetch();
    }

    private BooleanExpression cursorCondition(Long cursor, QChatMessage chatMessage) {
        if (cursor == null) {
            return null;
        }
        return chatMessage.chatMessageId.lt(cursor);
    }
}
