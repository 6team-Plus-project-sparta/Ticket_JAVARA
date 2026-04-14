CLAUDE.md — TicketJavara Backend

## 프로젝트 개요

TicketJavara — 공연·스포츠 티켓팅 플랫폼 백엔드 (B2C)
공정한 선착순 좌석 예매 + 선착순 쿠폰 발급 + 검색 캐싱

---

## 기술 스택

- Java 17 + Spring Boot 3.x
- Spring Data JPA + QueryDSL
- Spring Security + JWT (AccessToken 단독, 1시간 TTL)
- MySQL 8 (InnoDB, 타임존 KST) — serverTimezone=Asia/Seoul
- Redis 7 (Lettuce — 분산락 SETNX, Hold TTL, 인기검색어 ZSet)
- Caffeine (로컬 캐시, v2 검색)
- Docker + Docker Compose

---

## 패키지 구조

```
com.example.ticketjavara
├── global/                    # 앱 전체 공통 관심사
│   ├── config/                # SecurityConfig, RedisConfig, QuerydslConfig,
│   │                          # CacheConfig(@ConditionalOnProperty 캐시매니저 전환),
│   │                          # WebSocketConfig
│   ├── security/              # JwtUtil, JwtAuthFilter, JwtAuthEntryPoint,
│   │                          # JwtAccessDeniedHandler, CustomUserDetails
│   ├── lock/                  # DistributedLockService (Lettuce SETNX 공통),
│   │                          # LuaScripts (UNLOCK_SCRIPT, COUPON_DECR_SCRIPT)
│   ├── exception/             # GlobalExceptionHandler, BusinessException,
│   │                          # NotFoundException, ConflictException,
│   │                          # ForbiddenException, InvalidRequestException,
│   │                          # HoldExpiredException
│   ├── common/                # ApiResponse, ErrorResponse, BaseTimeEntity
│   └── util/                  # SecurityUtil (SecurityContextHolder userId 추출)
└── domain/
    ├── auth/                  # 인증 (회원가입·로그인·JWT)       ← 팀원 A
    ├── user/                  # 회원 프로필·내 정보              ← 팀원 A
    ├── event/                 # 이벤트 CRUD·구역·좌석            ← 팀원 B
    ├── search/                # 이벤트 검색 v1/v2·인기검색어     ← 팀원 B
    ├── booking/               # 좌석 Hold·주문·예매 확정         ← 팀원 C
    ├── coupon/                # 쿠폰 발급·적용·조회              ← 팀원 D
    └── chat/                  # CS 채팅 (WebSocket/STOMP, 도전) ← 팀원 D
```

---

## 각 도메인 패키지 내부 구조 (일관되게 유지)

```
domain/{도메인}/
├── controller/     # REST API
├── dto/
│   ├── request/    # 요청 DTO
│   └── response/   # 응답 DTO
├── entity/         # JPA 엔티티
├── enums/          # 도메인 Enum (해당 도메인에만 존재할 경우)
├── repository/     # Spring Data JPA Repository
└── service/        # 비즈니스 로직 (Facade 포함 시 별도 파일)
```

> **상세 파일 목록·Bean 의존 관계·HoldLockFacade 흐름도**는
> `11_백엔드_패키지_구조_설계서.md §2~§5` 참조.
>
> 핵심 Facade 구조 요약:
> ```
> HoldController
>   └─▶ HoldLockFacade        ← 트랜잭션 없음, Lettuce SETNX 획득 (waitTime=0, leaseTime=3s, Fail Fast)
>         └─▶ HoldService     ← @Transactional (DB 처리)
>         └─▶ HoldLockFacade  ← finally: Lua Script unlock() — COMMIT 이후 해제 보장
> ```

---

## 코딩 규칙

- 엔티티에 `@Setter` 사용 금지 → 비즈니스 메서드로 상태 변경
- DTO와 엔티티 분리 필수 — 컨트롤러에서 엔티티 직접 반환 금지
- 응답은 공통 응답 형식 사용: `ApiResponse { data, code, message }` (→ `12_공통_에러코드_설계서.md §8`)
- 예외는 `GlobalExceptionHandler`에서 일괄 처리 (try-catch 남용 금지)
- 서비스 메서드에 `@Transactional(readOnly=true)` 기본, 쓰기 메서드만 `@Transactional`
- 테스트: 단위 테스트(Mockito) + 동시성 테스트(ExecutorService + CyclicBarrier)
- 한국어 주석으로 핵심 비즈니스 로직 설명

---

## ERD 핵심 테이블 (v7.0 기준)

| 테이블 | 주요 컬럼 |
|--------|----------|
| USER | user_id PK, email UK, password, nickname, role ENUM[USER/ADMIN], created_at, updated_at |
| VENUE | venue_id PK, name, address — 시드데이터 1개 고정 (venue_id=1), 공연장 CRUD Out of Scope |
| EVENT | event_id PK, venue_id FK, created_by FK, title, category ENUM, event_date, sale_start_at, sale_end_at, round_number, status ENUM[ON_SALE/SOLD_OUT/CANCELLED/ENDED], description, thumbnail_url, created_at, updated_at |
| SECTION | section_id PK, event_id FK, section_name, price, total_seats |
| SEAT | seat_id PK, section_id FK, row_name, col_num — ⚠️ status 컬럼 없음! |
| ORDER | order_id PK, user_id FK, user_coupon_id FK nullable, total_amount, discount_amount, final_amount, status ENUM[PENDING/CONFIRMED/CANCELLED/FAILED], created_at, updated_at |
| BOOKING | booking_id PK, order_id FK, user_id FK, seat_id FK, event_id FK, original_price, ticket_code UK nullable, status ENUM[PENDING/CONFIRMED/CANCELLED/FAILED], created_at, updated_at |
| ACTIVE_BOOKING | seat_id PK, booking_id UK — 확정된 예약만 보관, 중복 확정 물리적 차단 |
| PAYMENT | payment_id PK, order_id FK, payment_key UK, method, paid_amount, status ENUM[SUCCESS/FAILED/REFUNDED], paid_at, refunded_at nullable |
| COUPON | coupon_id PK, name, discount_amount, total_quantity, remaining_quantity, start_at, expired_at |
| USER_COUPON | user_coupon_id PK, user_id FK, coupon_id FK, status ENUM[ISSUED/USED], issued_at, used_at nullable, version — @Version 낙관적 락, UNIQUE(user_id, coupon_id) — 중복 발급 방지 |
| CHAT_ROOM | chat_room_id PK, user_id FK, status ENUM[OPEN/CLOSED], created_at, updated_at |
| CHAT_MESSAGE | chat_message_id PK, chat_room_id FK, sender_id FK, sender_role ENUM[USER/ADMIN], content, sent_at |

---

## 좌석 상태 판단 로직 (SEAT에 status 컬럼 없음!)

```
1. ACTIVE_BOOKING에 seat_id 존재 → CONFIRMED
2. Redis hold:{eventId}:{seatId} 키 존재 → ON_HOLD
3. 그 외 → AVAILABLE
```

---

## 동시성 제어

| 시나리오 | 제어 방식 | 락 키 |
|----------|----------|-------|
| 좌석 Hold | Lettuce SETNX 분산락, Fail Fast | `lock:seat:{eventId}:{seatId}` |
| 쿠폰 발급 | Redis DECR + Lua Script 원자적 차감 (분산락 미사용 — DECR 자체가 원자적) | — |
| 쿠폰 사용 | `lock:user-coupon-use:{userCouponId}` 분산락 + `@Version` 낙관적 락 | `lock:user-coupon-use:{userCouponId}` |
| 쿠폰 복원(취소 시) | `SELECT FOR UPDATE` 비관적 락 | — |

**락 획득 순서 (데드락 방지):** ① 좌석 Hold 락 → ② 쿠폰 사용 락 → ③ DB 트랜잭션 (해제는 역순)

---

## 캐싱 전략 (3단계 진화)

- **v1 API**: 캐시 없음 — 매 요청마다 MySQL 직접 조회
- **v2 API (필수)**: Caffeine 로컬 캐시 — `@Cacheable` + `CaffeineCacheManager`, TTL 5분
- **v2 API (도전)**: Redis 원격 캐시 — `RedisTemplate` + Cache-Aside 패턴으로 교체
- **전환 방식**: `application.yml`의 `cache.provider` 값을 `caffeine` → `redis`로 변경만으로 전환
  (`@ConditionalOnProperty(name = "cache.provider", havingValue = "redis")`)
- **인기 검색어**: Redis ZSet (`search-keywords`) 실시간 Top 10 (ZINCRBY / ZREVRANGE)

### 캐시 TTL 설계

| 캐시 대상 | 저장소 | TTL | 캐시 적용 레이어 |
|-----------|--------|-----|----------------|
| 이벤트 검색 결과 | Caffeine → Redis | 5분 | `SearchService` (`@Cacheable("event-search")`) |
| 이벤트 상세 조회 | Caffeine | 10분 | `EventRepository` (`@Cacheable("event-detail")`) — 수정·삭제 시 `@CacheEvict` |
| 인기 검색어 Top 10 | Redis ZSet | TTL 없음 (ZSet 점수로 실시간 관리) | `SearchService` (ZINCRBY / ZREVRANGE) |

> **주의**: `EventController` 주석의 `💾 Caffeine TTL 10분` 표기는 해당 API가 캐시 적용 대상임을 표시한 것이며,
> 실제 `@Cacheable` 어노테이션은 **`EventRepository`** 레이어에 위치한다.

---

## 분산락 공통 모듈 (global/lock)

- `DistributedLockProvider` (인터페이스): `tryLock(key, value, ttlSeconds)`, `unlock(key, value)`
- `LettuceDistributedLock`: [필수] SETNX + Lua Script 구현체
- `RedissonDistributedLock`: [도전] RLock.tryLock() 구현체
- `LockConfig`: `@ConditionalOnProperty`로 구현체 Bean 전환
  - `lock.provider=lettuce` → `LettuceDistributedLock` (기본값, `matchIfMissing=true`)
  - `lock.provider=redisson` → `RedissonDistributedLock` + `RedissonClient` Bean 등록
- `LuaScripts`:
  - `UNLOCK_SCRIPT`: UUID 검증 후 DEL (본인 락만 해제)
  - `COUPON_DECR_SCRIPT`: DECR 후 < 0이면 INCR 원상복구 후 -1 반환

**캐시·락 Feature Flag 대칭 구조:**

```yaml
cache:
  provider: caffeine   # caffeine | redis    (ADR-006)
lock:
  provider: lettuce    # lettuce  | redisson (ADR-002)
```

---

## HoldLockFacade 패턴 (booking 도메인)

`@Transactional` ↔ Lettuce Lock 생명주기 충돌 방지를 위해 Facade로 분리.

```
HoldController → HoldLockFacade(락 획득) → HoldService(@Transactional) → HoldLockFacade(락 해제)
```

- 락 해제는 반드시 트랜잭션 COMMIT 이후 `finally` 블록에서 수행
- 상세 구조·잘못된 구조 비교는 `11_백엔드_패키지_구조_설계서.md §3-5` 참조

---

## 공통 응답 / 에러 형식

성공 응답 (`ApiResponse`):

```json
{
  "data": { ... },
  "code": "200",
  "message": "OK"
}
```

에러 응답 (`ErrorResponse`):

```json
{
  "code": "S002",
  "message": "다른 사용자가 처리 중입니다. 다른 좌석을 선택해주세요.",
  "timestamp": "2026-04-10T12:00:00"
}
```

> 에러코드 전체 목록(A/U/E/S/O/C/H/G 계열 39개), Exception 계층 구조, GlobalExceptionHandler 구현은
> **`12_공통_에러코드_설계서.md`** 참조.

---

## 서버 타임존

KST (UTC+9)

```yaml
spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Seoul
# MySQL: serverTimezone=Asia/Seoul
```

---

## 인프라 (로컬 개발)

Docker Compose: MySQL 8.0 (3306), Redis 7.0 (6379), App (8080)

| 항목 | 값 |
|------|---|
| MySQL DB명 | ticketjavara |
| MySQL 컨테이너명 | ticketjavara-mysql |
| Redis 컨테이너명 | ticketjavara-redis |

---

## 인프라 (AWS 실서비스)

| 컴포넌트 | 역할 |
|----------|------|
| EC2 (Public Subnet) | Spring Boot 앱 서버 |
| RDS MySQL 8 (Private Subnet) | 영속 데이터 Source of Truth |
| ElastiCache Redis (Private Subnet) | Hold TTL, 분산락, 인기검색어 |
| ECR | Docker 이미지 레지스트리 |
| ALB | HTTPS 트래픽 → EC2 라우팅, SSL 종료 |
| GitHub Actions | main 브랜치 push → 테스트 → 빌드 → ECR push → EC2 배포 |

---

## 주요 인덱스

| 테이블 | 인덱스 대상 | 타입 |
|--------|------------|------|
| EVENT | `(category, event_date)` | 복합 |
| EVENT | `title` | 단일 |
| BOOKING | `(seat_id, status)` | 복합 |
| BOOKING | `user_id` | 단일 |
| BOOKING | `order_id` | 단일 |
| ORDER | `user_id` | 단일 |
| USER_COUPON | `(coupon_id, user_id)` | 복합 UK |
| CHAT_MESSAGE | `(chat_room_id, chat_message_id DESC)` | 복합 |
| CHAT_ROOM | `(user_id, status)` | 복합 |

---

## 연결 문서

| 문서 | 주요 내용 |
|------|----------|
| `05_erd.md` | 전체 ERD v7.0, 테이블 설계 결정 사항 |
| `09_동시성_제어_설계서.md` | 동시성 시나리오 매트릭스, 테스트 코드 |
| `10_ADR.md` | 아키텍처 결정 기록 (ADR-001 ~ ADR-013) |
| `11_백엔드_패키지_구조_설계서.md` | 전체 파일 트리, Bean 의존 관계, HoldLockFacade 상세 |
| `12_공통_에러코드_설계서.md` | ErrorCode enum 전체, Exception 계층, GlobalExceptionHandler |
| `08_인프라_아키텍처.md` | AWS 구성도, Docker Compose, CI/CD 파이프라인 |
