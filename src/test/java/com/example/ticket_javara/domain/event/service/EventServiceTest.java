// =============================================
// EventServiceTest.java — 이벤트 서비스 단위 테스트
// 커버 테스트 ID: EVT-S-01 ~ EVT-S-06
// =============================================
package com.example.ticket_javara.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.ticket_javara.domain.event.dto.response.EventDetailResponseDto;
import com.example.ticket_javara.domain.event.entity.*;
import com.example.ticket_javara.domain.search.dto.response.EventSummaryResponseDto;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;

import com.example.ticket_javara.domain.event.dto.request.EventCreateRequestDto;
import com.example.ticket_javara.domain.event.dto.request.SectionCreateDto;
import com.example.ticket_javara.domain.event.repository.EventRepository;
import com.example.ticket_javara.domain.event.repository.SeatRepository;
import com.example.ticket_javara.domain.event.repository.SectionRepository;
import com.example.ticket_javara.domain.event.repository.VenueRepository;
import com.example.ticket_javara.global.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    // BUG-01 수정: EventDetailCacheService 분리에 따른 Mock 추가
    @Mock
    private EventDetailCacheService eventDetailCacheService;

    // resolveSeatStatus()에서 Redis hold 키 조회 시 사용
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private EventService eventService;

    // EVT-S-01
    @Test
    @DisplayName("이벤트 등록 성공 — Section + Seat 일괄 생성")
    void createEvent_Success() {
        // given
        Long adminUserId = 1L;
        Long venueId = 1L;

        User adminUser = User.builder()
                .email("admin@test.com")
                .password("encodedPassword")
                .nickname("관리자")
                .role(UserRole.ADMIN)
                .build();
        given(userRepository.findById(adminUserId)).willReturn(Optional.of(adminUser));

        given(eventRepository.save(any(Event.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(sectionRepository.save(any(Section.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SectionCreateDto section1 = SectionCreateDto.builder()
                .sectionName("VIP")
                .price(150000)
                .rowCount(2)
                .colCount(3)
                .build();

        SectionCreateDto section2 = SectionCreateDto.builder()
                .sectionName("R")
                .price(120000)
                .rowCount(2)
                .colCount(3)
                .build();

        EventCreateRequestDto requestDto = EventCreateRequestDto.builder()
                .title("BTS 월드투어 2026")
                .category(EventCategory.CONCERT)
                .venueId(venueId)
                .eventDate(LocalDateTime.now().plusDays(30))
                .saleStartAt(LocalDateTime.now().plusDays(10))
                .saleEndAt(LocalDateTime.now().plusDays(20))
                .roundNumber(1)
                .description("BTS 2026년 월드투어 서울 공연")
                .thumbnailUrl("https://example.com/thumbnail.jpg")
                .sections(List.of(section1, section2))
                .build();

        Venue venue = Venue.builder()
                .name("서울월드컵경기장")
                .address("서울시 마포구")
                .build();
        given(venueRepository.findById(venueId)).willReturn(Optional.of(venue));

        // when
        eventService.createEvent(requestDto, adminUserId);

        // then
        verify(eventRepository, times(1)).save(any(Event.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Seat>> seatListCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(seatRepository, times(2)).saveAll(seatListCaptor.capture());

        List<Seat> savedSeats = seatListCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        assertThat(savedSeats).hasSize(12); // 2 sections * 2 rows * 3 cols = 12

        for (Seat seat : savedSeats) {
            assertThat(seat.getRowName()).matches("^[A-B]$");
            assertThat(seat.getColNum()).isBetween(1, 3);
        }
    }

    // EVT-S-02
    @Test
    @DisplayName("이벤트 등록 실패 — 과거 eventDate 입력")
    void createEvent_Fail_PastEventDate() {
        // given
        Long adminUserId = 1L;

        EventCreateRequestDto requestDto = EventCreateRequestDto.builder()
                .title("BTS 월드투어 2026")
                .category(EventCategory.CONCERT)
                .venueId(1L)
                .eventDate(LocalDateTime.now().minusDays(1))
                .saleStartAt(LocalDateTime.now().plusDays(10))
                .saleEndAt(LocalDateTime.now().plusDays(20))
                .roundNumber(1)
                .description("description")
                .thumbnailUrl("url")
                .sections(List.of())
                .build();

        // when & then
        assertThatThrownBy(() -> eventService.createEvent(requestDto, adminUserId))
                .isInstanceOf(InvalidRequestException.class);
    }

    // EVT-S-03
    @Test
    @DisplayName("이벤트 등록 실패 — saleEndAt이 eventDate 이후")
    void createEvent_Fail_InvalidSaleEndAt() {
        // given
        Long adminUserId = 1L;

        User adminUser = User.builder()
                .email("admin@test.com")
                .password("encodedPassword")
                .nickname("관리자")
                .role(UserRole.ADMIN)
                .build();

        LocalDateTime eventDate = LocalDateTime.now().plusDays(30);
        LocalDateTime saleEndAt = LocalDateTime.now().plusDays(31); // eventDate보다 늦음

        EventCreateRequestDto requestDto = EventCreateRequestDto.builder()
                .title("BTS 월드투어 2026")
                .category(EventCategory.CONCERT)
                .venueId(1L)
                .eventDate(eventDate)
                .saleStartAt(LocalDateTime.now().plusDays(10))
                .saleEndAt(saleEndAt)
                .roundNumber(1)
                .description("description")
                .thumbnailUrl("url")
                .sections(List.of())
                .build();

        // when & then
        assertThatThrownBy(() -> eventService.createEvent(requestDto, adminUserId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("saleStartAt < saleEndAt < eventDate");
    }

    // EVT-S-04
    @Test
    @DisplayName("이벤트 등록 실패 — 존재하지 않는 venueId")
    void createEvent_Fail_VenueNotFound() {
        // given
        Long adminUserId = 1L;
        User adminUser = User.builder()
                .email("admin@test.com")
                .password("encodedPassword")
                .nickname("관리자")
                .role(UserRole.ADMIN)
                .build();
        given(userRepository.findById(adminUserId)).willReturn(Optional.of(adminUser));

        Long venueId = 999L;
        EventCreateRequestDto requestDto = EventCreateRequestDto.builder()
                .title("BTS 월드투어 2026")
                .category(EventCategory.CONCERT)
                .venueId(999L)
                .eventDate(LocalDateTime.now().plusDays(30))
                .saleStartAt(LocalDateTime.now().plusDays(10))
                .saleEndAt(LocalDateTime.now().plusDays(20))
                .roundNumber(1)
                .description("description")
                .thumbnailUrl("url")
                .sections(List.of(
                        SectionCreateDto.builder()
                                .sectionName("VIP")
                                .price(150000)
                                .rowCount(2)
                                .colCount(3)
                                .build()
                ))
                .build();

        given(venueRepository.findById(venueId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventService.createEvent(requestDto, adminUserId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("공연장");
    }

    // EVT-S-05
    @Test
    @DisplayName("이벤트 목록 조회 — category 필터 적용")
    void getEvents_FilterByCategory() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        Venue venue = Venue.builder()
                .name("서울월드컵경기장")
                .address("서울시 마포구")
                .build();

        Event event1 = mock(Event.class);
        given(event1.getCategory()).willReturn(EventCategory.CONCERT);
        given(event1.getVenue()).willReturn(venue);
        given(event1.getSections()).willReturn(List.of());
        given(event1.getTitle()).willReturn("BTS 월드투어 2026");
        given(event1.getEventDate()).willReturn(LocalDateTime.now().plusDays(30));
        given(event1.getThumbnailUrl()).willReturn("https://example.com/thumbnail.jpg");

        Event event2 = mock(Event.class);
        given(event2.getCategory()).willReturn(EventCategory.CONCERT);
        given(event2.getVenue()).willReturn(venue);
        given(event2.getSections()).willReturn(List.of());
        given(event2.getTitle()).willReturn("세븐틴 콘서트 2026");
        given(event2.getEventDate()).willReturn(LocalDateTime.now().plusDays(60));
        given(event2.getThumbnailUrl()).willReturn("https://example.com/thumbnail2.jpg");

        Page<Event> eventPage = new PageImpl<>(List.of(event1, event2), pageable, 2);

        given(eventRepository.findByCategory(eq(EventCategory.CONCERT), any(Pageable.class)))
                .willReturn(eventPage);

        // when
        Page<EventSummaryResponseDto> result = eventService.getEventList(EventCategory.CONCERT, null, pageable);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .allMatch(e -> e.getCategory() == EventCategory.CONCERT);
    }

    // EVT-S-06
    @Test
    @DisplayName("이벤트 목록 조회 — status 필터 적용")
    void getEvents_FilterByStatus() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        Venue venue = Venue.builder()
                .name("서울월드컵경기장")
                .address("서울시 마포구")
                .build();

        Event event1 = mock(Event.class);
        given(event1.getStatus()).willReturn(EventStatus.ON_SALE);
        given(event1.getCategory()).willReturn(EventCategory.CONCERT);
        given(event1.getVenue()).willReturn(venue);
        given(event1.getSections()).willReturn(List.of());
        given(event1.getTitle()).willReturn("BTS 월드투어 2026");
        given(event1.getEventDate()).willReturn(LocalDateTime.now().plusDays(30));
        given(event1.getThumbnailUrl()).willReturn("https://example.com/thumbnail.jpg");

        Event event2 = mock(Event.class);
        given(event2.getStatus()).willReturn(EventStatus.ON_SALE);
        given(event2.getCategory()).willReturn(EventCategory.CONCERT);
        given(event2.getVenue()).willReturn(venue);
        given(event2.getSections()).willReturn(List.of());
        given(event2.getTitle()).willReturn("세븐틴 콘서트 2026");
        given(event2.getEventDate()).willReturn(LocalDateTime.now().plusDays(60));
        given(event2.getThumbnailUrl()).willReturn("https://example.com/thumbnail2.jpg");

        Event event3 = mock(Event.class);
        given(event3.getStatus()).willReturn(EventStatus.ON_SALE);
        given(event3.getCategory()).willReturn(EventCategory.MUSICAL);
        given(event3.getVenue()).willReturn(venue);
        given(event3.getSections()).willReturn(List.of());
        given(event3.getTitle()).willReturn("뮤지컬 레미제라블");
        given(event3.getEventDate()).willReturn(LocalDateTime.now().plusDays(90));
        given(event3.getThumbnailUrl()).willReturn("https://example.com/thumbnail3.jpg");

        Page<Event> eventPage = new PageImpl<>(List.of(event1, event2, event3), pageable, 3);

        given(eventRepository.findByStatus(eq(EventStatus.ON_SALE), any(Pageable.class)))
                .willReturn(eventPage);

        // when
        Page<EventSummaryResponseDto> result = eventService.getEventList(null, EventStatus.ON_SALE, pageable);

        // then
        assertThat(result.getContent()).hasSize(3);
    }

    // EVT-S-07
    @Test
    @DisplayName("이벤트 목록 조회 — 페이징 정상 동작")
    void getEventList_Paging() {
        // given
        Pageable pageable = PageRequest.of(0, 3);

        Venue venue = Venue.builder()
                .name("서울월드컵경기장")
                .address("서울시 마포구")
                .build();

        List<Event> events = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Event event = mock(Event.class);
            given(event.getCategory()).willReturn(EventCategory.CONCERT);
            given(event.getVenue()).willReturn(venue);
            given(event.getSections()).willReturn(List.of());
            given(event.getTitle()).willReturn("이벤트 " + i);
            given(event.getEventDate()).willReturn(LocalDateTime.now().plusDays(i + 1));
            given(event.getThumbnailUrl()).willReturn("https://example.com/" + i);
            events.add(event);
        }

        Page<Event> eventPage = new PageImpl<>(events, pageable, 10); // 전체 10개 중 3개
        given(eventRepository.findAll(any(Pageable.class))).willReturn(eventPage);

        // when
        Page<EventSummaryResponseDto> result = eventService.getEventList(null, null, pageable);

        // then
        assertThat(result.getContent()).hasSize(3);           // 현재 페이지 size
        assertThat(result.getTotalElements()).isEqualTo(10);  // 전체 데이터 수
        assertThat(result.getTotalPages()).isEqualTo(4);      // 총 페이지 수 (10/3 올림)
        assertThat(result.getNumber()).isEqualTo(0);          // 현재 페이지 번호
    }

    // EVT-S-08
    @Test
    @DisplayName("이벤트 상세 조회 성공 — Section별 잔여 좌석 수 포함")
    void getEventDetail_Success_WithRemainingSeats() {
        // given
        Long eventId = 1L;

        Venue venue = Venue.builder()
                .name("서울월드컵경기장")
                .address("서울시 마포구")
                .build();

        // BUG-01 수정: eventDetailCacheService.getCachedEventDetail()이 기본 정보(remainingSeats=0) 반환
        // EventService.getEventDetail()이 캐시에서 가져온 후 seatRepository로 잔여 좌석 수를 실시간 주입
        EventDetailResponseDto.SectionDetailDto cachedSection1 = EventDetailResponseDto.SectionDetailDto.builder()
                .sectionId(1L).sectionName("A구역").price(110000).totalSeats(200)
                .remainingSeats(0L)  // placeholder
                .build();
        EventDetailResponseDto.SectionDetailDto cachedSection2 = EventDetailResponseDto.SectionDetailDto.builder()
                .sectionId(2L).sectionName("B구역").price(88000).totalSeats(150)
                .remainingSeats(0L)  // placeholder
                .build();
        EventDetailResponseDto cachedDto = EventDetailResponseDto.builder()
                .eventId(eventId)
                .title("BTS 월드투어 2026")
                .category(EventCategory.CONCERT)
                .venue(EventDetailResponseDto.VenueDto.builder()
                        .venueId(1L).name("서울월드컵경기장").address("서울시 마포구").build())
                .eventDate(LocalDateTime.now().plusDays(30))
                .description("BTS 공연")
                .thumbnailUrl("https://example.com/thumbnail.jpg")
                .sections(List.of(cachedSection1, cachedSection2))
                .build();

        given(eventDetailCacheService.getCachedEventDetail(eventId)).willReturn(cachedDto);
        // seatRepository는 EventService에서 직접 호출 (캐시 밖 실시간 조회)
        given(seatRepository.countAvailableSeatsBySectionId(1L)).willReturn(87L);
        given(seatRepository.countAvailableSeatsBySectionId(2L)).willReturn(120L);

        // when
        EventDetailResponseDto result = eventService.getEventDetail(eventId);

        // then
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getSections()).hasSize(2);
        assertThat(result.getSections().get(0).getRemainingSeats()).isEqualTo(87L);  // 실시간 조회값
        assertThat(result.getSections().get(1).getRemainingSeats()).isEqualTo(120L); // 실시간 조회값
        assertThat(result.getVenue().getName()).isEqualTo("서울월드컵경기장");
    }

    // EVT-S-09
    @Test
    @DisplayName("이벤트 상세 조회 실패 — 존재하지 않는 eventId")
    void getEventDetail_Fail_NotFound() {
        // given
        Long eventId = 999L;
        // BUG-01 수정: eventDetailCacheService가 NotFoundException을 던지도록 스텁
        given(eventDetailCacheService.getCachedEventDetail(eventId))
                .willThrow(new NotFoundException(ErrorCode.EVENT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> eventService.getEventDetail(eventId))
                .isInstanceOf(NotFoundException.class);
    }

}
