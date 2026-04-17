package com.example.ticket_javara.domain.event.service;

import com.example.ticket_javara.domain.booking.facade.HoldLockFacade;
import com.example.ticket_javara.domain.booking.service.HoldService;
import com.example.ticket_javara.domain.event.entity.*;
import com.example.ticket_javara.domain.event.repository.*;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.domain.event.dto.request.EventCreateRequestDto;
import com.example.ticket_javara.domain.event.dto.request.SectionCreateDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "cache.provider=caffeine",
        "spring.main.allow-bean-definition-overriding=true"  // ✅
})
class EventServiceCacheTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private EventRepository eventRepository;

    @MockitoBean
    private VenueRepository venueRepository;

    @MockitoBean
    private SectionRepository sectionRepository;

    @MockitoBean
    private SeatRepository seatRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private HoldLockFacade holdLockFacade;

    @MockitoBean
    private HoldService holdService;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean(name = "redisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    // 공통 픽스처
    private Venue venue;
    private Event mockEvent;
    private final Long EVENT_ID = 1L;

    @BeforeEach
    void setUp() {
        // 테스트 간 캐시 간섭 방지
        cacheManager.getCacheNames()
                .forEach(name -> {
                    var cache = cacheManager.getCache(name);
                    if (cache != null) cache.clear();
                });

        venue = Venue.builder()
                .name("서울월드컵경기장")
                .address("서울시 마포구")
                .build();

        mockEvent = mock(Event.class);
        given(mockEvent.getEventId()).willReturn(EVENT_ID);
        given(mockEvent.getTitle()).willReturn("BTS 월드투어 2026");
        given(mockEvent.getCategory()).willReturn(EventCategory.CONCERT);
        given(mockEvent.getVenue()).willReturn(venue);
        given(mockEvent.getEventDate()).willReturn(LocalDateTime.now().plusDays(30));
        given(mockEvent.getDescription()).willReturn("BTS 공연");
        given(mockEvent.getThumbnailUrl()).willReturn("https://example.com/thumbnail.jpg");
        given(mockEvent.getSections()).willReturn(List.of());

        given(eventRepository.findByIdWithVenueAndSections(EVENT_ID))
                .willReturn(Optional.of(mockEvent));
    }

    // EVT-S-10
    @Test
    @DisplayName("EVT-S-10: 이벤트 상세 조회 — Caffeine 캐시 HIT 확인")
    void getEventDetail_CacheHit() {
        // when — 동일 요청 2회
        eventService.getEventDetail(EVENT_ID);
        eventService.getEventDetail(EVENT_ID);

        // then — DB 조회는 1회만 발생 (2회째는 캐시 HIT)
        verify(eventRepository, times(1))
                .findByIdWithVenueAndSections(EVENT_ID);
    }

    // EVT-S-11
    @Test
    @DisplayName("EVT-S-11: 이벤트 등록 후 캐시 무효화 — @CacheEvict 동작")
    void createEvent_CacheEvict_AfterCreate() {
        // given
        User adminUser = User.builder()
                .email("admin@test.com")
                .password("encodedPassword")
                .nickname("관리자")
                .role(UserRole.ADMIN)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(adminUser));
        given(venueRepository.findById(1L)).willReturn(Optional.of(venue));
        given(eventRepository.save(any(Event.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(sectionRepository.save(any(Section.class)))
                .willAnswer(inv -> inv.getArgument(0));

        EventCreateRequestDto requestDto = EventCreateRequestDto.builder()
                .title("BTS 월드투어 2026")
                .category(EventCategory.CONCERT)
                .venueId(1L)
                .eventDate(LocalDateTime.now().plusDays(30))
                .saleStartAt(LocalDateTime.now().plusDays(10))
                .saleEndAt(LocalDateTime.now().plusDays(20))
                .roundNumber(1)
                .description("BTS 공연")
                .thumbnailUrl("https://example.com/thumbnail.jpg")
                .sections(List.of(
                        SectionCreateDto.builder()
                                .sectionName("VIP")
                                .price(150000)
                                .rowCount(2)
                                .colCount(3)
                                .build()
                ))
                .build();

        // when
        // 1단계: 첫 조회 → 캐시 PUT
        eventService.getEventDetail(EVENT_ID);

        // 2단계: 이벤트 등록 → EventCreatedEvent 발행 → @CacheEvict 발동
        eventService.createEvent(requestDto, 1L);

        // 3단계: 재조회 → 캐시 무효화됐으므로 DB 재조회
        eventService.getEventDetail(EVENT_ID);

        // then — DB 조회 총 2회 (캐시 무효화 확인)
        verify(eventRepository, times(2))
                .findByIdWithVenueAndSections(EVENT_ID);
    }
}
