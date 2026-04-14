# 📋 ADR (Architecture Decision Records) — TicketFlow

> **형식:** 결정마다 1개 ADR
> **문서 버전:** v1.0
> **최종 수정일:** 2026-04-10
> **연결 문서:** 1. 프로젝트 개요서 v3.0, 4. 기능명세서 v3.0, 5. ERD v6.0, 6. API 명세서 v1.0

---

## ADR-001. JWT AccessToken 단독 인증 채택 (Refresh Token 미구현)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-08 |
| **상태** | 승인됨 (v1 범위 축소 반영) |
| **결정자** | 팀 전원 |
| **관련 기능** | FN-AUTH-02, FN-AUTH-03 |

### 컨텍스트

회원 인증 방식을 결정해야 한다. 세션 기반과 JWT 기반을 비교하고, JWT 선택 시 Refresh Token 포함 여부를 결정해야 한다.

### 비교

| 항목 | 세션 기반 (Spring Session + Redis) | JWT AccessToken 단독 | JWT AccessToken + RefreshToken |
|------|-----------------------------------|---------------------|-------------------------------|
| 서버 상태 | Stateful (Redis 필수) | Stateless | Stateless + Redis(RefreshToken) |
| 스케일아웃 | Redis 세션 공유 필요 | 추가 인프라 불필요 | Redis 필요 |
| 구현 복잡도 | 낮음 | 낮음 | 중간 |
| 토큰 즉시 무효화 | 가능 (Redis 삭제) | 어려움 (만료 대기) | 가능 (RefreshToken 삭제) |
| 3주 일정 적합성 | ✅ | ✅ | ❌ (블랙리스트 로직 복잡) |

### 결정

**JWT AccessToken 단독 (만료 1시간), Refresh Token은 v1에서 제외**

### 이유

1. **일정**: Refresh Token 블랙리스트 관리(로그아웃 무효화, 탈취 대응, Redis TTL 동기화) 구현 복잡도가 3주 범위를 초과
2. **티켓팅 특성**: 예매 플로우(좌석 선택 → 결제 → 확정)는 1시간 이내 완료 → AccessToken 만료 전 예매 완료 가능
3. **스케일아웃**: Stateless JWT는 서버 증설 시 인증 로직 변경 없음
4. **실무 학습**: JWT 구조, HS256 서명, Spring Security 필터 적용 경험 확보

### 트레이드오프

AccessToken 만료(1시간) 전 강제 무효화 불가. 로그아웃 시 클라이언트 토큰 폐기만 수행.

**블랙리스트 방식 미채택 이유:**
```
블랙리스트 구현 시:
  - 로그아웃 → Redis에 "blocked:{accessToken}" SET EX 3600
  - 모든 API 요청 → Redis 블랙리스트 조회 추가
  → 매 요청마다 Redis I/O 발생 → Stateless JWT의 장점 상실
  → 3주 일정에서 구현·테스트 비용 대비 효과 낮음

∴ v1: 짧은 만료시간(1시간)으로 보안 위협 범위 최소화
  v2: RefreshToken 재도입 검토 (Redis 이미 존재하므로 인프라 추가 불필요)
```

---

> ### 📌 v1 구현 범위 정리 (팀원 필독)
>
> | 항목 | ADR 원래 설계 | v1 실제 구현 |
> |------|-------------|-------------|
> | AccessToken | HS256, 1시간 | HS256, 1시간 (동일) |
> | RefreshToken | 7일, Redis 저장 | **미구현** |
> | 로그아웃 | Redis RefreshToken 삭제 | 클라이언트 토큰 폐기만 |
>
> **영향 문서:** FN-AUTH-02(로그인), FN-AUTH-03(내 정보), UC-002, API 명세서 §1

---

## ADR-002. Lettuce SETNX 분산락(필수) + Redisson AOP(도전) — Feature Flag 전환 전략

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-08 |
| **최종 수정** | 2026-04-13 |
| **상태** | 승인됨 (Feature Flag 전략 추가 반영) |
| **결정자** | 팀 전원 |
| **관련 기능** | FN-SEAT-02, FN-BK-01, FN-BK-02, FN-CPN-02 |

### 컨텍스트

인기 공연 오픈 시 수천 명이 동시에 동일 좌석을 클릭한다. 1명만 예매에 성공해야 하고 나머지는 즉시 실패 응답을 받아야 한다. 동시성 제어 방식을 결정하고, 도전 구현(Redisson)으로의 전환을 안전하게 수행하는 방법도 함께 결정해야 한다.

### 분산락 전략 비교

| 전략 | 정합성 | 성능 | 구현 난이도 | 분산 환경 | UX |
|------|--------|------|------------|-----------|-----|
| 낙관적 락 (`@Version`) | 재시도 시 보장 | 충돌 적을 때 우수 | 중간 | 단일 DB 의존 | 재시도 지연 |
| 비관적 락 (`FOR UPDATE`) | 확실 | 락 경합 시 지연 | 낮음 | 단일 DB 의존 | 대기 발생 |
| Lettuce SETNX | 확실 | DB 접근 전 차단 | 중간 | 가능 | 즉시 실패 ✅ |
| Redisson | 확실 | DB 접근 전 차단 | 중간 (AOP) | 가능 | 즉시 실패 ✅ |

### 전환 전략 비교

| 전략 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **코드 직접 교체** | Lettuce 코드를 Redisson으로 덮어씀 | 단순 | 롤백 시 Git 되돌리기 필요, 전환 중 위험 |
| **Feature Flag** ✅ | `lock.provider` 설정값으로 Bean 전환 | 코드 변경 없이 전환·롤백, 캐시 전략과 동일 패턴 | 인터페이스 추상화 구현 필요 |

### 결정

**① Lettuce SETNX(필수) → ② Redisson AOP(도전) 순차 적용 + Feature Flag로 무중단 전환**

캐시의 `cache.provider: caffeine | redis` 패턴과 동일하게, 락도 `lock.provider: lettuce | redisson` 설정 한 줄로 전환한다.

```yaml
# application.yml
lock:
  provider: lettuce   # 필수 구현 완료 후 → redisson 으로 변경만으로 전환
```

### Feature Flag 구현 구조

**1단계: 공통 인터페이스 정의 (`DistributedLockProvider`)**

```java
// global/lock/DistributedLockProvider.java
public interface DistributedLockProvider {
    /**
     * 분산락 획득 시도 (Fail Fast — waitTime=0)
     * @return true: 획득 성공 / false: 획득 실패
     */
    boolean tryLock(String key, String value, long ttlSeconds);

    /**
     * 분산락 해제 (본인 락만 해제 보장)
     */
    void unlock(String key, String value);
}
```

**2단계: 구현체 두 개 (Lettuce / Redisson)**

```java
// global/lock/LettuceDistributedLock.java
@Component("lettuceLockProvider")
@RequiredArgsConstructor
public class LettuceDistributedLock implements DistributedLockProvider {
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryLock(String key, String value, long ttlSeconds) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds))
        );
    }

    @Override
    public void unlock(String key, String value) {
        // Lua Script — UUID 검증 후 원자적 DEL (본인 락만 해제)
        redisTemplate.execute(LuaScripts.UNLOCK_SCRIPT,
            List.of(key), value);
    }
}

// global/lock/RedissonDistributedLock.java
@Component("redissonLockProvider")
@RequiredArgsConstructor
public class RedissonDistributedLock implements DistributedLockProvider {
    private final RedissonClient redissonClient;

    @Override
    public boolean tryLock(String key, String value, long ttlSeconds) {
        try {
            RLock lock = redissonClient.getLock(key);
            return lock.tryLock(0, ttlSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String key, String value) {
        RLock lock = redissonClient.getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**3단계: Feature Flag로 Bean 선택 (`LockConfig`)**

```java
// global/config/LockConfig.java
@Configuration
public class LockConfig {

    // lock.provider=lettuce (기본값) → LettuceDistributedLock 활성화
    @Bean
    @Primary
    @ConditionalOnProperty(name = "lock.provider", havingValue = "lettuce", matchIfMissing = true)
    public DistributedLockProvider lettuceLockProvider(StringRedisTemplate redisTemplate) {
        return new LettuceDistributedLock(redisTemplate);
    }

    // lock.provider=redisson → RedissonDistributedLock 활성화
    @Bean
    @Primary
    @ConditionalOnProperty(name = "lock.provider", havingValue = "redisson")
    public DistributedLockProvider redissonLockProvider(RedissonClient redissonClient) {
        return new RedissonDistributedLock(redissonClient);
    }

    // Redisson 클라이언트 — lock.provider=redisson일 때만 생성
    @Bean
    @ConditionalOnProperty(name = "lock.provider", havingValue = "redisson")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host}") String host,
                                         @Value("${spring.data.redis.port}") int port) {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}
```

**4단계: 사용 측은 인터페이스만 바라봄 — 전환 시 코드 변경 없음**

```java
// booking/facade/HoldLockFacade.java
@Component
@RequiredArgsConstructor
public class HoldLockFacade {
    private final DistributedLockProvider lockProvider; // Lettuce든 Redisson이든 무관

    public HoldResponseDto hold(Long eventId, Long seatId, Long userId) {
        String lockKey = "lock:seat:" + eventId + ":" + seatId;
        String uuid    = UUID.randomUUID().toString();

        if (!lockProvider.tryLock(lockKey, uuid, 3)) {
            throw new ConflictException(ErrorCode.SEAT_LOCK_FAILED);
        }
        try {
            return holdService.processHold(eventId, seatId, userId); // @Transactional
        } finally {
            lockProvider.unlock(lockKey, uuid); // COMMIT 이후 해제 보장
        }
    }
}
```

### 전환 절차

```
[1단계] 동시성 테스트 작성 → 실패 확인
        └ DistributedLockProvider 인터페이스 및 LettuceDistributedLock 구현
        └ LockConfig (lock.provider=lettuce, matchIfMissing=true) 등록

[2단계] Lettuce 구현 → 테스트 통과 확인 (필수)
        └ lock.provider: lettuce (기본값) 상태로 운영

        ↓ 도전 구현

[3단계] Redisson AOP 구현
        └ RedissonDistributedLock 구현체 추가
        └ LockConfig에 redisson Bean 등록
        └ @RedisLock 커스텀 어노테이션 + @Aspect 구조 추가 (선택 — AOP 적용 시)
        └ 동일 테스트 코드로 Redisson 검증

[4단계] Feature Flag로 전환
        └ application.yml: lock.provider: lettuce → redisson
        └ 코드 변경 없음, 재배포만으로 전환
        └ 문제 발생 시 → lock.provider: lettuce 로 즉시 롤백
```

### 캐시 전략과 대칭 구조

```yaml
# application.yml — 두 Feature Flag 나란히 관리
cache:
  provider: caffeine   # caffeine | redis    (ADR-006)
lock:
  provider: lettuce    # lettuce  | redisson (ADR-002)
```

### 이유

1. **발제 요구사항**: Lettuce 필수 구현 → Redisson 도전 순서 준수
2. **안전한 전환**: yml 한 줄 변경으로 전환·롤백 — Git 되돌리기 불필요
3. **Fail Fast 유지**: 인터페이스 계약(`waitTime=0`) 양쪽 구현체 모두 동일하게 적용
4. **테스트 재사용**: 동일 동시성 테스트 코드로 Lettuce·Redisson 양쪽 모두 검증 가능
5. **이중 방어선 유지**: Redis 분산락(1차) + ACTIVE_BOOKING PK 제약(2차) — 락 구현체 교체와 무관하게 유지
6. **캐시와 패턴 일관성**: `cache.provider`와 동일한 `@ConditionalOnProperty` 패턴 → 팀 내 학습 부하 최소화

### 트레이드오프

| 상황 | 대응 |
|------|------|
| Redis 장애 | ACTIVE_BOOKING PK 제약이 최후 방어선으로 동작 |
| leaseTime(3초) 초과 | hold 키 존재 여부로 2차 차단 |
| 단일 EC2 → 스케일아웃 | Redisson은 Redis Cluster 연결만으로 분산 환경 대응 |
| Redisson 전환 후 문제 | `lock.provider: lettuce` 한 줄로 즉시 롤백 |
| Lettuce·Redisson 클라이언트 공존 | `RedissonClient` Bean은 `lock.provider=redisson`일 때만 생성 — 불필요한 커넥션 없음 |

---

## ADR-003. ACTIVE_BOOKING 별도 테이블 분리 (SEAT.status 삭제)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-09 |
| **상태** | 승인됨 |
| **결정자** | 팀 전원 |
| **관련 ERD** | ERD v7.0 §2-2, §2-3 |

### 컨텍스트

좌석 확정(CONFIRMED) 상태를 어디에 저장할지 결정해야 한다.

MySQL은 `WHERE status='CONFIRMED'` 조건부 유니크 인덱스(Partial Index)를 지원하지 않는다.
`SEAT.status` 컬럼에 CONFIRMED를 저장하면 "동일 좌석의 두 번째 CONFIRMED 행을 막는" 유니크 인덱스를 만들 수 없다.

### 비교

| 방법 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **SEAT.status 컬럼** | `AVAILABLE / ON_HOLD / CONFIRMED` 컬럼 | 구현 단순 | MySQL Partial Index 미지원 → 중복 CONFIRMED 방어 불가 |
| **ACTIVE_BOOKING 분리** | 확정 예약만 별도 테이블, seat_id PK | PK 제약으로 DB 레벨 중복 차단 | 조인 필요, 테이블 추가 |

### 결정

**ACTIVE_BOOKING 별도 테이블 분리, SEAT.status 컬럼 삭제**

```sql
CREATE TABLE active_booking (
    seat_id    BIGINT PRIMARY KEY,     -- 좌석당 1건만 허용 (DB 레벨 방어)
    booking_id BIGINT UNIQUE NOT NULL,
    FOREIGN KEY (seat_id)    REFERENCES seat(seat_id),
    FOREIGN KEY (booking_id) REFERENCES booking(booking_id)
);
```

### 좌석 상태 판단 로직

```
1. ACTIVE_BOOKING에 seat_id 존재 → CONFIRMED
2. Redis hold:{eventId}:{seatId} 존재 → ON_HOLD
3. 그 외 → AVAILABLE
```

### 이유

1. **DB 레벨 최후 방어**: Redis 분산락 버그나 장애 시에도 PK 중복 에러로 중복 확정 원천 차단
2. **상태 책임 분리**: ON_HOLD는 Redis(TTL), CONFIRMED는 MySQL(영속), AVAILABLE은 두 곳 모두 없는 상태
3. **이력 보존**: BOOKING 테이블에 모든 상태 변경 이력이 남음. 취소 시 ACTIVE_BOOKING DELETE + BOOKING.status=CANCELLED

---

## ADR-004. ORDER_ITEM 삭제 → BOOKING 흡수

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-09 |
| **상태** | 승인됨 |
| **관련 ERD** | ERD v7.0 §2-1 |

### 컨텍스트

ORDER(주문)와 BOOKING(좌석 예약) 사이에 ORDER_ITEM 중간 테이블이 필요한가?

### 비교

| 방법 | 구조 | 장점 | 단점 |
|------|------|------|------|
| **ORDER_ITEM 유지** | ORDER → ORDER_ITEM → BOOKING | 확장성 (1 주문 = N 상품 유형) | JOIN 뎁스 증가 |
| **BOOKING 흡수** | ORDER → BOOKING (order_id FK 직접 보유) | JOIN 단순, 구현 빠름 | ORDER_ITEM 확장성 포기 |

### 결정

**ORDER_ITEM 삭제 — BOOKING이 `order_id FK`, `original_price` 직접 보유**

### 이유

1. **MVP 범위**: TicketFlow는 좌석 단위 예매만 다룸. ORDER_ITEM이 표현하는 "다양한 상품 유형" 불필요
2. **개발 속도**: 3주 일정에서 JOIN 뎁스 감소 → 구현·테스트 속도 향상
3. **원가 스냅샷**: `BOOKING.original_price`로 결제 당시 가격을 보존하여 ORDER_ITEM의 스냅샷 역할 대체

---

## ADR-005. BOOKING이 티켓 역할 겸임 (TICKET 테이블 미분리, C-02 B안)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-10 |
| **상태** | 승인됨 (SA 피드백 C-02 B안 반영) |
| **관련 ERD** | ERD v7.0 §2-1 |

### 컨텍스트

SA 피드백에서 "API 웹훅 응답에 `ticketId`, `ticketCode`를 반환해야 하므로 별도 TICKET 테이블이 필요하다"는 의견이 제기됐다.

### 비교

| 구분 | A안: TICKET 테이블 분리 | B안: BOOKING이 티켓 역할 |
|------|----------------------|------------------------|
| 테이블 수 | +1 (TICKET 추가) | 변경 없음 |
| JOIN 복잡도 | 증가 (BOOKING → TICKET) | 유지 |
| API ticketId | TICKET.ticket_id | BOOKING.booking_id |
| 확장성 | 재발급·여러 장 발급 유리 | MVP 범위에서 충분 |
| 3주 일정 적합성 | ❌ | ✅ |

### 결정

**B안 채택 — `BOOKING.ticket_code` 컬럼 추가, BOOKING이 티켓 역할 겸임**

```sql
ALTER TABLE booking ADD COLUMN ticket_code VARCHAR(50) UNIQUE NULL
  COMMENT '예매 확정 시 발급. 형식: TF-{year}-{sectionAbbr}-{seatNum}-{uuid4자리}';
```

웹훅 응답의 `bookings[]` 배열은 `bookingId`와 `ticketCode`로 구성한다.

### 이유

1. **단순 구조**: JOIN 뎁스 증가 없이 BOOKING 단일 테이블에서 티켓 정보 조회 가능
2. **MVP 적합**: 재발급·이전이 없는 1:1 구조에서 별도 테이블의 이점 없음
3. **확장 경로 확보**: 향후 재발급·이전이 필요할 경우 TICKET 테이블로 분리 가능 (BOOKING.ticket_code → TICKET FK로 마이그레이션)

---

## ADR-006. 캐싱 3단계 진화 전략 (v1 → v2 Caffeine → v2 Redis)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-08 |
| **상태** | 승인됨 |
| **관련 기능** | FN-SRCH-01, FN-SRCH-02, FN-SRCH-03, FN-SRCH-04 |

### 컨텍스트

발제 요구사항에 따라 캐싱 전략을 단계적으로 구현해야 한다. 처음부터 Redis를 쓰지 않고 Caffeine을 먼저 적용하는 이유를 결정해야 한다.

### 결정

**3단계 순차 진화 — 단계를 건너뛰지 않는다**

```
[v1 API] 캐시 없음 (기준선 측정)
         ↓ 필수
[v2 API] Caffeine 로컬 캐시 (@Cacheable + Caffeine)
         ↓ 도전 (v1 대비 50% 이상 단축 확인 후)
[v2 API] Redis Cache-Aside 패턴으로 교체
```

### 전환 방식 (`@ConditionalOnProperty`)

```yaml
# application.yml — 값 변경만으로 캐시 저장소 전환
cache:
  provider: caffeine   # → redis 변경 시 Redis Cache-Aside로 전환
```

```java
@ConditionalOnProperty(name = "cache.provider", havingValue = "caffeine")
@Bean CacheManager caffeineCacheManager() { ... }

@ConditionalOnProperty(name = "cache.provider", havingValue = "redis")
@Bean CacheManager redisCacheManager() { ... }
```

### 캐시 대상 및 TTL

| 대상 | 저장소 | TTL | 무효화 트리거 |
|------|--------|-----|-------------|
| 이벤트 검색 결과 | Caffeine → Redis | 5분 | 이벤트 등록 시 `@CacheEvict` |
| 이벤트 상세 조회 | Caffeine | 10분 | 이벤트 수정 시 `@CacheEvict` |
| 인기 검색어 Top 10 | Redis ZSet | 없음 (ZINCRBY 실시간 관리) | - |

### 이유

1. **학습 목적**: Caffeine과 Redis의 성능 차이·한계를 직접 체험 → 로컬 캐시의 Scale-out 불일치 문제 인지
2. **점진적 복잡도**: Caffeine(단순) → Redis(복잡) 순서로 팀 학습 부하 분산
3. **설정 기반 전환**: 코드 변경 없이 `application.yml` 값만 바꿔 전환 → 무중단 비교 가능

---

## ADR-007. 인기 검색어 — Redis ZSet 실시간 집계

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-08 |
| **상태** | 승인됨 |
| **관련 기능** | FN-SRCH-04 |

### 컨텍스트

실시간 인기 검색어 Top 10을 제공해야 한다. 집계 방식을 결정해야 한다.

### 비교

| 방법 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **DB 집계** | 검색 로그 테이블 + GROUP BY | 영속성 | 매 조회마다 집계 쿼리 부하 |
| **Redis ZSet** | `ZINCRBY search-keywords 1 {keyword}` | 원자적, O(log N) | Redis 유실 시 집계 초기화 |
| **Caffeine 집계** | 로컬 메모리 카운터 | 빠름 | Scale-out 시 서버별 불일치 |

### 결정

**Redis ZSet (`ZINCRBY`) 실시간 집계**

```
검색 발생 시: ZINCRBY search-keywords 1 {keyword}
Top 10 조회: ZREVRANGE search-keywords 0 9 WITHSCORES
```

### 이유

1. **원자적 연산**: ZINCRBY는 단일 명령으로 점수 증가 → 동시 집계 시 정합성 보장
2. **O(log N) 정렬**: ZSet이 자동 정렬 유지 → 별도 ORDER BY 불필요
3. **Redis 장애 시 Graceful Degradation**: 빈 배열 반환, 검색 기능 영향 없음

---

## ADR-008. 쿠폰 발급 — 분산락 없이 Redis DECR 단독 (L-01)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-10 |
| **상태** | 승인됨 (SA 피드백 L-01 반영) |
| **관련 기능** | FN-CPN-02 |

### 컨텍스트

초기 설계에서 선착순 쿠폰 발급에 Redis 분산락 + DECR 이중 잠금을 사용했으나, SA 피드백(L-01)에서 이중 잠금의 비효율성이 지적됐다.

### 문제

```
[기존 설계]
lock:coupon:{couponId} 분산락 획득 → Redis DECR → 분산락 해제

문제: DECR은 Redis 단일 명령으로 이미 원자적이다.
     추가 분산락은 불필요한 이중 잠금으로 처리량을 오히려 저하시킨다.
     (분산락이 직렬화를 강제 → 초당 처리량 = 1 / lockTimeout)
```

### 결정

**Redis DECR + Lua Script 단독 사용 (분산락 제거)**

```lua
-- 음수 방어 Lua Script
local val = redis.call('DECR', KEYS[1])
if val < 0 then
    redis.call('INCR', KEYS[1])  -- 원상복구
    return -1                     -- 소진 신호
end
return val
```

### MySQL remaining_quantity 동기화 (m-06)

Redis DECR 성공 직후 MySQL `remaining_quantity` 즉시 차감:
```sql
UPDATE coupon SET remaining_quantity = remaining_quantity - 1 WHERE coupon_id = ?
```

Redis 유실 시 MySQL 기준으로 Redis 카운터 재초기화:
```sql
SELECT remaining_quantity FROM coupon WHERE coupon_id = ?
-- → Redis SET coupon:stock:{id} {remaining_quantity}
```

### 이유

1. **원자성 중복 제거**: DECR 자체가 원자적 → 추가 락 불필요
2. **처리량 향상**: 분산락 없이 Redis 원자적 명령만으로 500명 동시 요청 처리 가능
3. **DB Fallback 보장**: Redis 연결 실패 시 `SELECT ... FOR UPDATE` 경로로 전환

---

## ADR-009. 검색 파라미터 — 가격 필터를 EXISTS 서브쿼리로 처리 (m-02)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-10 |
| **상태** | 승인됨 |
| **관련 기능** | FN-SRCH-01, FN-SRCH-02 |

### 컨텍스트

이벤트 검색 시 `minPrice/maxPrice` 필터가 있다. 한 이벤트에는 여러 구역(SECTION)이 있고 구역마다 가격이 다르다. 필터 의미를 정의해야 한다.

### 결정

**"구역 중 하나라도 해당 가격 범위에 속하면 이벤트 노출" — EXISTS 서브쿼리 사용**

```sql
EXISTS (
    SELECT 1 FROM section s
    WHERE s.event_id = e.event_id
      AND s.price BETWEEN :minPrice AND :maxPrice
)
```

```java
// QueryDSL BooleanBuilder
if (minPrice != null || maxPrice != null) {
    builder.and(JPAExpressions.selectOne()
        .from(section)
        .where(section.eventId.eq(event.id)
            .and(minPrice != null ? section.price.goe(minPrice) : null)
            .and(maxPrice != null ? section.price.loe(maxPrice) : null))
        .exists());
}
```

### 이유

1. **사용자 관점**: "8만원 예산으로 볼 수 있는 공연" → B구역(8만원)이 있으면 노출이 자연스러운 UX
2. **중복 제거**: JOIN 사용 시 구역 수만큼 결과 행이 중복 → DISTINCT 필요 vs EXISTS는 첫 매칭 즉시 종료
3. **인덱스 활용**: `(event_id, price)` 복합 인덱스와 결합 시 EXISTS 내부 조회가 효율적

---

## ADR-010. 페이징 전략 — 도메인별 이분화

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-08 |
| **상태** | 승인됨 |
| **관련 API** | GET /api/events, GET /api/users/me/bookings, GET /api/chat/rooms/{id}/messages |

### 컨텍스트

TicketFlow의 세 가지 주요 목록 조회(이벤트, 내 예매 내역, 채팅 이력)에 대해 페이징 방식을 결정해야 한다.

### 비교

| 항목 | Offset 기반 (`LIMIT/OFFSET`) | Cursor 기반 (`createdAt + id`) |
|------|---------------------------|------------------------------|
| 구현 난이도 | 낮음 | 중간 |
| 대량 데이터 성능 | OFFSET 커질수록 Full Scan | 일정 (인덱스 Range Scan) |
| 총 페이지 수 표시 | 가능 | 불가능 |
| 실시간 데이터 삽입 시 | 중복/누락 발생 | 없음 |
| 특정 페이지 직접 이동 | 가능 | 불가능 |

### 결정

| 도메인 | 방식 | 이유 |
|--------|------|------|
| 이벤트 목록/검색 | **Offset** (`page`, `size`) | 5,000개 이벤트 탐색, 총 페이지 수 표시 필요 |
| 내 예매 내역 | **Offset** (`page`, `size`) | 사용자가 직접 페이지 선택하는 패턴 |
| 채팅 이력 | **Cursor** (`cursor`, `size`) | 실시간 메시지 추가 환경 — Offset은 누락/중복 위험 |

### 이유 (채팅에 Cursor를 선택한 근거)

```
T=0: 메시지 100개 있음. 사용자가 page=1 (50~100번) 조회
T=1: 새 메시지 5개 추가 (101~105번)
T=2: 사용자가 page=2 (1~50번) 조회 시

Offset 방식: OFFSET 50 → 46~95번 메시지 반환 (51~100번 중 5개 누락)
Cursor 방식: WHERE id < 50 ORDER BY id DESC → 정확히 1~49번 반환
```

---

## ADR-011. WebSocket + STOMP 채택 (CS 채팅, 도전 기능)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-08 |
| **상태** | 승인됨 (도전 기능) |
| **관련 기능** | FN-CHAT-01, FN-CHAT-02, FN-CHAT-03 |

### 컨텍스트

고객↔관리자 간 실시간 1:1 CS 채팅을 구현해야 한다. 실시간 통신 방식을 결정해야 한다.

### 비교

| 항목 | Long Polling | SSE | WebSocket + STOMP |
|------|-------------|-----|-------------------|
| 통신 방향 | 단방향 | 단방향 (서버→클라이언트) | ✅ 양방향 |
| 채팅 적합성 | 부적합 | 부적합 (전송에 별도 HTTP POST 필요) | ✅ 단일 연결로 송수신 |
| 서버 부하 | 높음 (반복 HTTP) | 낮음 | 낮음 |
| Spring 지원 | ✅ | ✅ | ✅ `spring-websocket` |
| 채팅방 라우팅 | 직접 구현 | 직접 구현 | ✅ STOMP pub/sub 자동화 |

### 결정

**WebSocket + STOMP (Spring 내장 Simple Broker)**

```
연결:      ws://api.ticketflow.io/ws-stomp
구독(사용자): /sub/chat/room/{chatRoomId}
구독(관리자): /sub/chat/rooms  (신규 채팅방 알림)
전송:      /pub/chat/message
```

### STOMP를 선택한 이유 (순수 WebSocket 대비)

```
순수 WebSocket:
  - 텍스트 프레임을 직접 파싱해야 함
  - 채팅방 라우팅, 브로드캐스트 로직 직접 구현

STOMP over WebSocket:
  - SUBSCRIBE /sub/chat/room/{id} → 자동 라우팅
  - @MessageMapping으로 수신 처리
  - SimpleBroker가 구독자 관리 자동화
```

### 트레이드오프

외부 메시지 브로커(RabbitMQ, Redis Pub/Sub) 없이 Spring 내장 Simple Broker 사용. 서버 재시작 시 구독 정보 초기화. 스케일아웃 시 서버 간 메시지 공유 불가 → 향후 Redis Pub/Sub 브로커로 전환 필요.

---

## ADR-012. 취소 API — DELETE 대신 POST /cancel 사용

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-09 |
| **상태** | 승인됨 |
| **관련 API** | POST /api/orders/{orderId}/cancel |

### 컨텍스트

주문 취소를 `DELETE /api/orders/{orderId}`로 표현할지, `POST /api/orders/{orderId}/cancel`로 표현할지 결정해야 한다.

### 결정

**`POST /api/orders/{orderId}/cancel` 사용**

### 이유

1. **의미론**: 취소는 리소스를 삭제하는 것이 아니라 상태를 CANCELLED로 전이시키는 것 → `DELETE`는 부적절
2. **복잡한 부수 효과**: ACTIVE_BOOKING DELETE, BOOKING/ORDER 상태 변경, USER_COUPON 복원, Mock PG 환불 요청 → 단순 `DELETE` 시맨틱으로 표현 불가
3. **HTTP 멱등성**: `DELETE`는 멱등성을 기대하지만, 취소는 이미 취소된 주문에 재요청 시 400을 반환해야 함

### 비교 (`Hold 해제는 DELETE 사용`)

Hold 해제(`DELETE /api/events/{eventId}/seats/{seatId}/hold`)는 Hold 리소스 자체를 삭제하는 단순한 의미이므로 `DELETE`가 적합하다. 반면 주문 취소는 복잡한 상태 전이를 동반하므로 `POST /cancel`이 적합하다.

---

## ADR-013. holdToken 역조회 구조 — Redis 별도 키 방식 A안 (C-01)

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-04-10 |
| **상태** | 승인됨 (비판적 점검 C-01 A안 반영) |
| **관련 기능** | FN-SEAT-02, FN-BK-01 |

### 컨텍스트

Hold 성공 시 `holdToken(UUID)`를 발급한다. 주문 생성(`POST /api/orders`) 시 `holdTokens[]`를 받아 (eventId, seatId, userId)를 역조회해야 한다.

기존 설계에서는 `hold:{eventId}:{seatId} = userId` 키만 저장했기 때문에, holdToken → (eventId, seatId)를 역조회할 방법이 없었다.

### 비교

| 방안 | 설명 | 장단점 |
|------|------|--------|
| **A안 (채택)** | `holdToken:{uuid} = "{eventId}:{seatId}:{userId}"` 별도 키 | API 구조 유지, Redis 키 2개 |
| B안 | `holdTokens[]` 대신 `seatIds[]`로 Request 변경 | API 변경 필요, 클라이언트 영향 |

### 결정

**A안 채택 — `holdToken:{uuid}` Redis 키 추가 (TTL 300초, hold 키와 동일)**

```
Hold 성공 시 Redis 저장:
  SET  hold:{eventId}:{seatId}    = {userId}                      (TTL 300초)
  SET  holdToken:{uuid}           = "{eventId}:{seatId}:{userId}" (TTL 300초)
  INCR user-hold-count:{userId}                                   (TTL 300초)

주문 생성 시:
  GET holdToken:{uuid} → "{eventId}:{seatId}:{userId}" 파싱 → Hold 검증
```

### 이유

1. **API 하위 호환**: `holdTokens[]` Request 구조 유지 → 클라이언트 변경 없음
2. **TTL 일치**: holdToken 키 TTL = hold 키 TTL → 토큰 만료 = Hold 만료로 자연스럽게 동기화
3. **구현 단순**: 별도 DB 조회 없이 Redis 단일 GET으로 역조회 완료

---

## 주요 변경 이력

| 버전 | ADR | 변경 내용 | 일자 |
|------|-----|----------|------|
| v1.0 | 전체 | 최초 작성 | 2026-04-10 |
| v1.1 | ADR-002 | Lettuce → Redisson 전환 전략을 코드 직접 교체 방식에서 **Feature Flag (`lock.provider`) 방식**으로 개정. `DistributedLockProvider` 인터페이스 추상화, `LockConfig` Bean 전환 구조, 전환 절차 추가 | 2026-04-13 |
