package com.example.ticket_javara.domain.chat.repository;

import com.example.ticket_javara.domain.chat.entity.ChatRoom;
import com.example.ticket_javara.domain.chat.entity.ChatRoomStatus;
import com.example.ticket_javara.domain.chat.entity.QChatRoom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class ChatRoomRepositoryImpl implements ChatRoomRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<ChatRoom> findLatestOpenRoomByUserId(Long userId) {
        QChatRoom chatRoom = QChatRoom.chatRoom;

        ChatRoom result = queryFactory
                .selectFrom(chatRoom)
                .join(chatRoom.user).fetchJoin()  // N+1 방지를 위한 fetch join
                .where(
                        chatRoom.user.userId.eq(userId),
                        chatRoom.status.in(ChatRoomStatus.WAITING, ChatRoomStatus.IN_PROGRESS)
                )
                .orderBy(chatRoom.createdAt.desc())
                .limit(1)
                .fetchOne();

        return Optional.ofNullable(result);
    }
}