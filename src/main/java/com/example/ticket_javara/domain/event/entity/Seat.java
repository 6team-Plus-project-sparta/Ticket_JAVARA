package com.example.ticket_javara.domain.event.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * SEAT 테이블 엔티티
 * ERD v7.0: seat_id, section_id FK, row_name, col_num
 * ⚠️ status 컬럼 없음!
 *    좌석 상태는 ACTIVE_BOOKING 존재 여부(CONFIRMED) + Redis hold 키 존재 여부(ON_HOLD)로 판단
 */
@Entity
@Table(name = "seat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(nullable = false, length = 20)
    private String rowName;     // 예: "A열"

    @Column(nullable = false)
    private Integer colNum;     // 예: 15

    @Builder
    public Seat(Section section, String rowName, Integer colNum) {
        this.section = section;
        this.rowName = rowName;
        this.colNum = colNum;
    }
}
