package com.example.ticket_javara.global.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TicketFlow 초기 데이터 생성기 (JdbcTemplate batchUpdate 방식)
 *
 * ─────────────────────────────────────────────────────────────
 * 실행 순서
 *   @Order(1) 이 클래스 — Venue(1개) + Admin(1명) + Coupon(3종) + Event/Section/Seat 벌크
 *
 * 생성 데이터 (SA 문서 기준)
 *   VENUE   : 1개 고정 (venue_id=1, 올림픽공원 체조경기장)
 *   USER    : Admin 1명 (role=ADMIN, BCrypt 암호화)
 *   COUPON  : 3종 (콘서트 10% / 뮤지컬 20% / 스포츠 5000원)
 *   EVENT   : 5,000개 (카테고리 4종 × 1,250개)
 *   SECTION : 이벤트당 3~6개 (평균 약 22,500개)
 *   SEAT    : 구역당 100~150석 (평균 약 2,812,500개)
 *
 * 중복 실행 방지
 *   events 테이블 25건 초과 시 전체 스킵
 *
 * 실행 환경
 *   spring.profiles.active=local 또는 docker
 *
 * 성능 주의사항 (최초 실행 5~10분 소요)
 *   application-local.yml 에 반드시 아래 설정 추가:
 *     spring.jpa.properties.hibernate.jdbc.batch_size: 500
 *     spring.jpa.properties.hibernate.order_inserts: true
 *     logging.level.org.hibernate.SQL: WARN   ← 미설정 시 IDE 콘솔 폭발
 * ─────────────────────────────────────────────────────────────
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
@Profile({"local", "docker"})
public class BulkDataInit implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    // ===================== 상수 (SA 문서 기준) =====================

    private static final int TARGET_EVENT_COUNT = 5_000;

    /** JdbcTemplate batchUpdate 단위 크기 */
    private static final int EVENT_BATCH   = 500;
    private static final int SECTION_BATCH = 1_000;
    private static final int SEAT_BATCH    = 5_000;

    private static final int SECTION_MIN  = 3;
    private static final int SECTION_MAX  = 6;
    private static final int ROW_COUNT    = 10;        // 고정 10행
    private static final int COL_MIN      = 10;        // 최소 10열 → 100석
    private static final int COL_MAX      = 15;        // 최대 15열 → 150석

    private static final Random RND = new Random(42);  // 시드 고정 → 재현 가능

    private static final String[] ROW_NAMES =
            {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};

    // ===================== 카테고리 메타데이터 =====================

    /** EventCategory.values() 순서와 동일하게 유지 */
    private static final String[] CATEGORIES = {"CONCERT", "MUSICAL", "THEATER", "SPORTS"};

    private static final String[][] EVENT_NAMES = {
        // CONCERT (0)
        {"아이유 콘서트", "BTS 월드투어", "뉴진스 팬미팅", "세븐틴 앙코르", "임영웅 전국투어",
         "아이브 쇼케이스", "엑소 컴백쇼", "투애니원 리유니온", "빅뱅 스페셜", "에스파 월드투어"},
        // MUSICAL (1)
        {"레미제라블", "오페라의 유령", "맘마미아", "위키드", "캣츠",
         "해밀턴", "라이온킹", "시카고", "노트르담 드 파리", "모차르트"},
        // THEATER (2)
        {"햄릿", "맥베스", "오이디푸스왕", "갈매기", "벚꽃동산",
         "세일즈맨의 죽음", "고도를 기다리며", "욕망이라는 이름의 전차", "인형의 집", "유리동물원"},
        // SPORTS (3)
        {"LG vs 두산", "KIA vs 삼성", "SSG vs NC", "롯데 vs 한화", "키움 vs KT",
         "현대모비스 vs DB", "SK vs 서울삼성", "전주 KCC vs 울산", "수원 KT vs 창원LG", "고양 소노 vs 원주DB"}
    };

    private static final String[][] SECTION_NAMES = {
        // CONCERT
        {"VIP석", "R석", "S석", "A석", "B석", "스탠딩"},
        // MUSICAL
        {"VIP석", "R석", "S석", "A석", "B석", "C석"},
        // THEATER
        {"VIP석", "R석", "S석", "A석", "B석", "C석"},
        // SPORTS
        {"1루 응원석", "3루 응원석", "내야 지정석", "외야 일반석", "VIP박스", "익사이팅석"}
    };

    /** 카테고리별 구역별 가격 (위 SECTION_NAMES 순서와 1:1 대응) */
    private static final int[][] SECTION_PRICES = {
        {170_000, 130_000,  99_000, 77_000, 55_000, 45_000},  // CONCERT
        {150_000, 120_000,  90_000, 70_000, 50_000, 35_000},  // MUSICAL
        {100_000,  80_000,  60_000, 45_000, 30_000, 20_000},  // THEATER
        { 80_000,  70_000,  50_000, 35_000, 25_000, 15_000}   // SPORTS
    };

    // ================================================================
    //  진입점
    // ================================================================

    @Override
    public void run(ApplicationArguments args) {
        // ── 중복 실행 방지 ──────────────────────────────────────
        Long eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event", Long.class);
        if (eventCount != null && eventCount > 25) {
            log.info("[BulkDataInit] 이미 초기화됨, 건너뜀 (event={}건)", eventCount);
            return;
        }

        log.info("[BulkDataInit] ===== 초기 데이터 삽입 시작 =====");
        long total = System.currentTimeMillis();

        // ── 1. Venue ───────────────────────────────────────────
        long venueId = insertVenue();

        // ── 2. Admin ───────────────────────────────────────────
        long adminId = insertAdmin();

        // ── 3. Coupon (테스트용 3종) ────────────────────────────
        insertCoupons();

        // ── 4. Event + Section + Seat ──────────────────────────
        insertEventsWithSectionsAndSeats(venueId, adminId);

        log.info("[BulkDataInit] ===== 전체 완료 / 총 소요: {}ms =====",
                System.currentTimeMillis() - total);
    }

    // ================================================================
    //  1. VENUE — 1개 고정 (venue_id=1)
    // ================================================================

    private long insertVenue() {
        // 이미 있으면 스킵 후 기존 ID 반환
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM venue", Long.class);
        if (existing != null && existing > 0) {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT venue_id FROM venue ORDER BY venue_id ASC LIMIT 1", Long.class);
            log.info("[BulkDataInit] VENUE 이미 존재 (venue_id={}), 스킵", id);
            return id != null ? id : 1L;
        }

        jdbcTemplate.update(
                "INSERT INTO venue (name, address) VALUES (?, ?)",
                "올림픽공원 체조경기장",
                "서울특별시 송파구 올림픽로 424"
        );
        Long id = jdbcTemplate.queryForObject(
                "SELECT venue_id FROM venue ORDER BY venue_id DESC LIMIT 1", Long.class);
        log.info("[BulkDataInit] VENUE 생성 완료 (venue_id={})", id);
        return id != null ? id : 1L;
    }

    // ================================================================
    //  2. ADMIN 계정
    //     SA 문서: ADMIN은 시드 데이터로만 생성 (회원가입 API 불가)
    // ================================================================

    private long insertAdmin() {
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Long.class);
        if (existing != null && existing > 0) {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM users WHERE role = 'ADMIN' LIMIT 1", Long.class);
            log.info("[BulkDataInit] ADMIN 이미 존재 (user_id={}), 스킵", id);
            return id != null ? id : 1L;
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO users (email, password, nickname, role, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                "admin@ticketflow.io",
                passwordEncoder.encode("Admin1234!"),   // BCrypt 암호화
                "관리자",
                "ADMIN",
                now,
                now
        );
        Long id = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE role = 'ADMIN' LIMIT 1", Long.class);
        log.info("[BulkDataInit] ADMIN 생성 완료 (email=admin@ticketflow.io, user_id={})", id);
        return id != null ? id : 1L;
    }

    // ================================================================
    //  3. COUPON — 테스트용 3종
    //     - 쿠폰 발급 동시성 테스트에 바로 사용 가능한 수량으로 설정
    //     - Redis 초기화는 CouponService.registerCoupon()에서 담당하므로
    //       여기서는 MySQL 레코드만 INSERT (remaining_quantity도 함께 저장)
    // ================================================================

    private void insertCoupons() {
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coupon", Long.class);
        if (existing != null && existing > 0) {
            log.info("[BulkDataInit] COUPON 이미 존재 ({}건), 스킵", existing);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // (name, discount_amount, total_quantity, remaining_quantity, start_at, expired_at)
        List<Object[]> coupons = List.of(
            new Object[]{
                "콘서트 17000원 할인",
                17_000,
                100,    // total_quantity
                100,    // remaining_quantity  ← Redis DECR 동시성 테스트용 수량
                now,
                now.plusDays(30)
            },
            new Object[]{
                "뮤지컬 30000원 할인",
                30_000,
                50,
                50,
                now,
                now.plusDays(30)
            },
            new Object[]{
                "스포츠 5000원 할인",
                5_000,
                200,
                200,
                now,
                now.plusDays(30)
            }
        );

        jdbcTemplate.batchUpdate(
            "INSERT INTO coupon " +
            "(name, discount_amount, total_quantity, remaining_quantity, start_at, expired_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            coupons
        );
        log.info("[BulkDataInit] COUPON {}종 생성 완료", coupons.size());
        log.info("▶ 쿠폰 동시성 테스트: coupon_id=1 (수량 100장)을 대상으로 실습 가능");
    }

    // ================================================================
    //  4. EVENT + SECTION + SEAT 벌크 INSERT
    // ================================================================

    private void insertEventsWithSectionsAndSeats(long venueId, long adminId) {
        int perCategory = TARGET_EVENT_COUNT / CATEGORIES.length; // 1,250개씩

        for (int catIdx = 0; catIdx < CATEGORIES.length; catIdx++) {
            long catStart = System.currentTimeMillis();
            String category   = CATEGORIES[catIdx];
            String[] namePool = EVENT_NAMES[catIdx];

            // ── EVENT 배치 수집 ─────────────────────────────────
            List<Object[]> eventBatch   = new ArrayList<>(EVENT_BATCH);
            // 각 이벤트의 섹션 수 및 열 수 미리 결정 (RANDOM 순서 보장)
            int[][]        sectionMeta  = new int[perCategory][];

            for (int i = 0; i < perCategory; i++) {
                int sectionCount  = SECTION_MIN + RND.nextInt(SECTION_MAX - SECTION_MIN + 1);
                sectionMeta[i]    = new int[sectionCount];
                for (int s = 0; s < sectionCount; s++) {
                    sectionMeta[i][s] = COL_MIN + RND.nextInt(COL_MAX - COL_MIN + 1);
                }
            }

            // ── 500개 단위 EVENT INSERT → Section/Seat 연쇄 생성 ─
            for (int i = 0; i < perCategory; i++) {
                String baseName  = namePool[i % namePool.length];
                int    roundNum  = (i / namePool.length) + 1;
                LocalDateTime eventDate   = generateEventDate(i);
                LocalDateTime saleStartAt = eventDate.minusDays(30);
                LocalDateTime saleEndAt   = eventDate.minusHours(1);
                String status    = determineStatus(eventDate);
                LocalDateTime now = LocalDateTime.now();

                eventBatch.add(new Object[]{
                    venueId,
                    adminId,
                    baseName,
                    category,
                    eventDate,
                    saleStartAt,
                    saleEndAt,
                    roundNum,
                    status,
                    baseName + " " + roundNum + "회차 공연입니다.",
                    "https://cdn.ticketflow.io/events/" + catIdx + "_" + (i % namePool.length) + ".jpg",
                    now,
                    now
                });

                // EVENT_BATCH 단위로 INSERT 후 SECTION/SEAT 연쇄 처리
                if (eventBatch.size() >= EVENT_BATCH) {
                    flushEventBatch(eventBatch, sectionMeta,
                                    i - EVENT_BATCH + 1, catIdx);
                    eventBatch.clear();
                }
            }
            // 카테고리 마지막 잔여분 처리
            if (!eventBatch.isEmpty()) {
                int startIdx = perCategory - eventBatch.size();
                flushEventBatch(eventBatch, sectionMeta, startIdx, catIdx);
                eventBatch.clear();
            }

            log.info("[BulkDataInit] [{}] EVENT {}개 완료 ({}ms)",
                    category, perCategory, System.currentTimeMillis() - catStart);
        }
    }

    /**
     * EVENT 배치를 INSERT하고 방금 INSERT된 event_id 목록으로
     * SECTION → SEAT 를 연쇄 생성한다.
     *
     * @param eventBatch  INSERT할 EVENT 파라미터 목록
     * @param sectionMeta 이벤트별 섹션 수 및 열 수 배열 (카테고리 내 전체 인덱스 기준)
     * @param metaOffset  sectionMeta에서 이 배치의 시작 인덱스
     * @param catIdx      카테고리 인덱스 (0=CONCERT … 3=SPORTS)
     */
    private void flushEventBatch(List<Object[]> eventBatch,
                                  int[][] sectionMeta,
                                  int metaOffset,
                                  int catIdx) {

        // ── EVENT INSERT ────────────────────────────────────────
        jdbcTemplate.batchUpdate(
            "INSERT INTO event " +
            "(venue_id, created_by, title, category, event_date, " +
            " sale_start_at, sale_end_at, round_number, status, " +
            " description, thumbnail_url, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            eventBatch
        );

        // ── 방금 INSERT된 event_id 목록 조회 ────────────────────
        // 배치 내 건수만큼 최신 ID를 내림차순으로 가져온 뒤 오름차순 정렬
        int batchSize = eventBatch.size();
        List<Long> eventIds = jdbcTemplate.queryForList(
            "SELECT event_id FROM event ORDER BY event_id DESC LIMIT ?",
            Long.class, batchSize
        );
        // DESC로 가져왔으므로 역순 → 삽입 순서(오름차순)로 복원
        java.util.Collections.reverse(eventIds);

        // ── SECTION + SEAT INSERT ───────────────────────────────
        List<Object[]> sectionBatch = new ArrayList<>(SECTION_BATCH);
        List<Object[]> seatBatch    = new ArrayList<>(SEAT_BATCH);

        String[] sectionNames = SECTION_NAMES[catIdx];
        int[]    prices       = SECTION_PRICES[catIdx];

        for (int e = 0; e < eventIds.size(); e++) {
            long eventId     = eventIds.get(e);
            int  metaIdx     = metaOffset + e;
            int  sectionCount = sectionMeta[metaIdx].length;

            for (int s = 0; s < sectionCount; s++) {
                int colCount   = sectionMeta[metaIdx][s];
                int totalSeats = ROW_COUNT * colCount;  // 100~150석

                sectionBatch.add(new Object[]{
                    eventId,
                    sectionNames[s],
                    prices[s],
                    totalSeats
                });

                // SECTION 배치 flush
                if (sectionBatch.size() >= SECTION_BATCH) {
                    flushSectionBatch(sectionBatch, seatBatch);
                    sectionBatch.clear();
                }
            }
        }
        // 잔여 SECTION flush
        if (!sectionBatch.isEmpty()) {
            flushSectionBatch(sectionBatch, seatBatch);
        }
        // 잔여 SEAT flush
        if (!seatBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "INSERT INTO seat (section_id, row_name, col_num) VALUES (?, ?, ?)",
                seatBatch
            );
            seatBatch.clear();
        }
    }

    /**
     * SECTION 배치를 INSERT하고 즉시 SEAT 배치를 누적한다.
     * SEAT 배치가 SEAT_BATCH 이상이 되면 flush한다.
     */
    private void flushSectionBatch(List<Object[]> sectionBatch, List<Object[]> seatBatch) {

        // ── SECTION INSERT ──────────────────────────────────────
        jdbcTemplate.batchUpdate(
            "INSERT INTO section (event_id, section_name, price, total_seats) " +
            "VALUES (?, ?, ?, ?)",
            sectionBatch
        );

        // ── 방금 INSERT된 section_id 목록 조회 ──────────────────
        int batchSize = sectionBatch.size();
        List<Long> sectionIds = jdbcTemplate.queryForList(
            "SELECT section_id FROM section ORDER BY section_id DESC LIMIT ?",
            Long.class, batchSize
        );
        java.util.Collections.reverse(sectionIds);

        // total_seats 정보는 sectionBatch[3] 에 담겨 있음 → colCount 역산
        for (int i = 0; i < sectionIds.size(); i++) {
            long sectionId = sectionIds.get(i);
            int  totalSeats = (int) sectionBatch.get(i)[3];
            int  colCount   = totalSeats / ROW_COUNT;   // total_seats = ROW_COUNT × colCount

            // ── SEAT 파라미터 누적 ──────────────────────────────
            for (int row = 0; row < ROW_COUNT; row++) {
                for (int col = 1; col <= colCount; col++) {
                    seatBatch.add(new Object[]{sectionId, ROW_NAMES[row], col});

                    // SEAT 배치 flush
                    if (seatBatch.size() >= SEAT_BATCH) {
                        jdbcTemplate.batchUpdate(
                            "INSERT INTO seat (section_id, row_name, col_num) VALUES (?, ?, ?)",
                            seatBatch
                        );
                        seatBatch.clear();
                    }
                }
            }
        }
    }

    // ================================================================
    //  유틸 메서드
    // ================================================================

    /**
     * 이벤트 날짜 생성 (KST 기준)
     * SA 문서: 서버 타임존 KST (UTC+9)
     * 현재 기준 -180일 ~ +180일 사이에 고르게 분산
     */
    private LocalDateTime generateEventDate(int index) {
        int daysOffset = (index % 361) - 180;
        int hour       = 14 + RND.nextInt(5);   // 14~18시 사이
        return LocalDateTime.now()
                .plusDays(daysOffset)
                .withHour(hour)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

    /**
     * 이벤트 날짜 기준 status 결정
     * ERD 기준: ON_SALE / SOLD_OUT / CANCELLED / ENDED
     *
     * 분포: 과거 → ENDED, 미래 → ON_SALE(90%) / SOLD_OUT(5%) / CANCELLED(5%)
     */
    private String determineStatus(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now())) {
            return "ENDED";
        }
        int roll = RND.nextInt(100);
        if (roll < 5)  return "SOLD_OUT";
        if (roll < 10) return "CANCELLED";
        return "ON_SALE";
    }
}
