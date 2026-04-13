package com.example.ticket_javara.domain.event.entity;

import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * EVENT 테이블 엔티티
 * ERD v7.0: event_id, venue_id FK, created_by FK, title, category, event_date,
 *           sale_start_at, sale_end_at, round_number, status, description, thumbnail_url,
 *           created_at, updated_at
 * ⚠️ @Setter 사용 금지
 */
@Entity
@Table(name = "event",
        indexes = {
                @Index(name = "idx_event_search", columnList = "category, event_date"),
                @Index(name = "idx_event_title", columnList = "title")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @Column(nullable = false)
    private LocalDateTime saleStartAt;

    @Column(nullable = false)
    private LocalDateTime saleEndAt;

    @Column(nullable = false)
    private Integer roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String thumbnailUrl;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Section> sections = new ArrayList<>();

    @Builder
    public Event(Venue venue, User createdBy, String title, EventCategory category,
                 LocalDateTime eventDate, LocalDateTime saleStartAt, LocalDateTime saleEndAt,
                 Integer roundNumber, EventStatus status, String description, String thumbnailUrl) {
        this.venue = venue;
        this.createdBy = createdBy;
        this.title = title;
        this.category = category;
        this.eventDate = eventDate;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
        this.roundNumber = roundNumber;
        this.status = status;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
    }

    /** 이벤트 상태 변경 */
    public void updateStatus(EventStatus newStatus) {
        this.status = newStatus;
    }
}
