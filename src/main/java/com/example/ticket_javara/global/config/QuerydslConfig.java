package com.example.ticket_javara.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * QueryDSL + JPA Auditing 설정
 * - JPAQueryFactory: QueryDSL 동적 쿼리 빌더 (이벤트 검색 v1/v2에서 사용)
 * - @EnableJpaAuditing: BaseTimeEntity(createdAt, updatedAt) 자동 관리
 */
@Configuration
@EnableJpaAuditing
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
