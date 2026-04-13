package com.example.ticket_javara.domain.event.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * SECTION 테이블 엔티티
 * ERD v7.0: section_id, event_id FK, section_name, price, total_seats
 */
@Entity
@Table(name = "section")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "section_id")
    private Long sectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 100)
    private String sectionName;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer totalSeats;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();

    @Builder
    public Section(Event event, String sectionName, Integer price, Integer totalSeats) {
        this.event = event;
        this.sectionName = sectionName;
        this.price = price;
        this.totalSeats = totalSeats;
    }
}
