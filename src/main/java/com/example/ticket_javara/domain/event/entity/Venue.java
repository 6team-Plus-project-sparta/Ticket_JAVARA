package com.example.ticket_javara.domain.event.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * VENUE 테이블 엔티티
 * ERD v7.0: venue_id, name, address
 * ⚠️ venue_id=1로 고정 (공연장 CRUD는 Out of Scope)
 */
@Entity
@Table(name = "venue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "venue_id")
    private Long venueId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String address;

    @Builder
    public Venue(String name, String address) {
        this.name = name;
        this.address = address;
    }
}
