Char# CarrotSettle

> 커머스 플랫폼 정산 시스템 MVP — **주문 → 구매 확정 → 수수료 계산 → 정산 배치 → 정산 내역 조회**의 전체 흐름을 End-to-End로 구현한 학습용 레퍼런스 프로젝트

## 프로젝트 배경

실무에서는 정산 데이터를 **소비하는 쪽**(스크래핑 → 대출 한도 계산)을 다뤘지만, 정산 데이터를 **만드는 쪽**(주문 → 정산 → 지급)은 직접 구현해본 적이 없었다. 이 프로젝트는 그 갭을 메우기 위해 주문부터 정산 집계까지의 전체 흐름을 직접 설계·구현한 결과물이다.

정산 시스템은 커머스/핀테크 백엔드의 핵심 도메인이지만, 전체 흐름을 실무 수준으로 공개한 오픈소스 레퍼런스는 드물다. CarrotSettle은 `BigDecimal` 반올림·상태 머신·Spring Batch Chunk·동시성 제어 같은 실무 패턴을 한 저장소에서 확인할 수 있는 참고 구현을 목표로 한다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.x |
| ORM | JPA + QueryDSL |
| DB | PostgreSQL 16 |
| Cache | Redis 7 |
| Batch | Spring Batch |
| Migration | Flyway |
| Test | JUnit 5 + Testcontainers |
| Docs | Swagger (springdoc-openapi) |
| Infra | Docker Compose |

## 도메인 모델

```
Seller → Product → OrderItem → Order → Payment → Settlement → Payout
```

- **주문 상태 전이**: `CREATED → PAID → CONFIRMED → SETTLED` / `PAID → REFUNDED`
  - 주문 생성 시 즉시 `PAID`로 전이 (MVP는 결제 시뮬레이션 생략)
  - 전이 규칙은 `OrderStatus.TRANSITIONS` 맵에 선언적으로 정의, 잘못된 전이 시 `IllegalStateException`
- **수수료 구조**
  - PG 수수료: 결제 금액의 3% (고정)
  - 플랫폼 수수료: 셀러 등급별 (일반 5% / 우수 3% / VIP 1%)
- **금액 계산**: 반드시 `BigDecimal` + `RoundingMode.HALF_UP`. API 응답 직렬화 시 `.setScale(0, RoundingMode.HALF_UP).longValue()`

## 주요 기능 및 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/v1/orders` | 주문 생성 (생성 즉시 PAID 상태) |
| `PATCH` | `/api/v1/orders/{id}/confirm` | 수동 구매 확정 |
| `PATCH` | `/api/v1/orders/{id}/refund` | 환불 처리 (PAID → REFUNDED, 정산 대상 제외) |
| `GET` | `/api/v1/settlements` | 정산 내역 조회 (sellerId, 기간, status 필터 + 페이징) |
| `POST` | `/api/v1/settlements/calculate` | 정산 배치 수동 트리거 |

구매 확정은 매일 새벽 2시 `@Scheduled`로도 자동 실행된다 (`OrderScheduler` 빈).

## 아키텍처 및 기술 선택 포인트

### 모듈 구성

```
domain/          ← 순수 도메인 (Entity, VO, 계산 로직)
infrastructure/  ← JPA, QueryDSL, Redis 설정
api/             ← Controller, Service, DTO, 스케줄러
```

- `infrastructure`는 `java-library` + `api` 설정으로 `domain`과 `spring-boot-starter-data-jpa`를 상위 모듈에 전이 노출
- `api`는 `implementation project(':domain')`으로 도메인 접근, `spring-tx`를 직접 선언하여 `@Transactional` 사용

### 기술 선택

- **수수료 계산**: `FeeCalculationService.calculate(amount, grade)` 단일 진입점. 각 수수료는 전용 Calculator(`PgFeeCalculator`, `PlatformFeeCalculator`)로 분리
- **수수료 VO**: `@Embeddable FeeDetail` (PG 수수료, 플랫폼 수수료, 총 수수료)
- **정산 배치**: Spring Batch Chunk — `JpaPagingItemReader` → `ItemProcessor` → `ItemWriter`. chunk 처리 후 `EntityManager.clear()`로 OOM 방지
- **정산 조회**: QueryDSL 동적 쿼리 + Redis Cache Aside (확정 데이터 TTL 1시간, 진행 중 5분)
- **복합 인덱스**: `(seller_id, settlement_date, status)`
- **동시성 제어**: `@Version` 낙관적 락 + `@Lock(PESSIMISTIC_WRITE)` 비관적 락 + Spring Retry
- **N+1 해결**: 시나리오별로 `JOIN FETCH` / `@BatchSize` 구분 사용, Hibernate Statistics로 쿼리 수 검증

## 실행 방법

Docker가 실행 중이어야 한다. `spring-boot-docker-compose` 의존성으로 `bootRun` 시 PostgreSQL/Redis가 자동 기동된다.

```bash
# 빌드
./gradlew build

# 애플리케이션 실행
./gradlew :api:bootRun

# 컴파일 확인
./gradlew compileJava
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## 테스트

테스트는 Testcontainers로 PostgreSQL/Redis 컨테이너를 자동 관리한다.

```bash
# 전체 테스트
./gradlew test

# 단일 클래스
./gradlew test --tests "com.haeni.carrot.settle.TargetTest"

# 단일 메서드
./gradlew test --tests "com.haeni.carrot.settle.TargetTest.methodName"
```

## 개발 마일스톤

| 단계 | 기간 | 목표 |
|------|------|------|
| **M1** | 1~2주차 | 프로젝트 세팅 + 주문 생성 |
| **M2** | 3~4주차 | 구매 확정 + 수수료 계산 |
| **M3** | 5~6주차 | 정산 배치 + 조회 (MVP 완료) |
| **M4** | 7주차 | 가상 PG 결제 + Actuator/Prometheus/Grafana 모니터링 |
| **M5** | 8~9주차 | Redisson 분산 락 + k6 부하 테스트 |
| **M6** | 10주차 | 지급 처리 상태 머신 |
| **M7** | 11~12주차 | `@Async` + Kafka 이벤트 기반 전환 + CI/CD |

## 문서

- [PRD](.claude/PRD_정산시스템_MVP.md) — 제품 요구사항 명세서
- [도메인 설계](docs/domain-design.md) — Bounded Context 및 Aggregate Root 설계
- [코드 컨벤션](docs/code-conventions.md) — URL/JSON/금액/날짜/Enum/DTO 규칙
- [CLAUDE.md](CLAUDE.md) — Claude Code 작업 가이드

## 범위 외 (Out of Scope)

MVP에서는 아래 항목을 의도적으로 제외한다.

- 실제 PG 결제 연동 (MVP는 주문 생성 시 PAID로 직접 전환)
- 로그인/인증/인가 (셀러 ID는 API 파라미터로 직접 전달)
- 프론트엔드 UI (Swagger UI로 검증)
- 포인트/쿠폰/프로모션 할인, 부분 환불, 배송 관리, 알림
