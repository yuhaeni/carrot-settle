# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository

- **GitHub:** `https://github.com/yuhaeni/carrot-settle`
- **Main Branch:** `main`

## Commands

```bash
# 빌드
./gradlew build

# 테스트 전체 실행
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.haeni.carrot.settle.TargetTest"

# 단일 테스트 메서드 실행
./gradlew test --tests "com.haeni.carrot.settle.TargetTest.methodName"

# 애플리케이션 실행 (Docker Compose 자동 기동 포함)
./gradlew :api:bootRun

# 컴파일만 확인
./gradlew compileJava
```

테스트는 Testcontainers를 사용하므로 Docker가 실행 중이어야 한다. `TestcontainersConfiguration`이 PostgreSQL과 Redis 컨테이너를 자동으로 띄운다.

## Architecture

커머스 플랫폼의 **주문 → 구매 확정 → 수수료 계산 → 정산 배치 집계 → 정산 내역 조회** 흐름을 구현하는 정산 시스템 MVP.

### 도메인 모델

```
Seller → Product → OrderItem → Order → Payment → Settlement → Payout
```

- **Order ↔ Seller 관계**: `Order`에 `seller_id` 없음. Seller는 `Order → OrderItem → Product → Seller` 체인으로 접근 (PRD 도메인 모델 기준)
- **수수료 구조**: PG 3% (고정) + 플랫폼 수수료 셀러 등급별 (일반 5% / 우수 3% / VIP 1%)
- **주문 상태 전이**: `CREATED → PAID → CONFIRMED → SETTLED` / `PAID → REFUNDED` (주문 생성 시 즉시 PAID로 전이). 전이 규칙은 `OrderStatus.TRANSITIONS` 맵에 선언적으로 정의. 잘못된 전이 시 `validateTransitionTo()`가 `IllegalStateException` 발생
- **금액 계산**: 반드시 `BigDecimal`과 `RoundingMode.HALF_UP` 사용

### 주요 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/v1/orders` | 주문 생성 (생성 즉시 PAID 상태) |
| `PATCH` | `/api/v1/orders/{id}/confirm` | 수동 구매 확정 |
| `GET` | `/api/v1/settlements` | 정산 내역 조회 (sellerId, 기간, status 필터 + 페이징) |
| `POST` | `/api/v1/settlements/calculate` | 정산 배치 수동 트리거 |

구매 확정은 매일 새벽 2시 `@Scheduled` 자동 실행도 병행한다.

### 기술 선택 포인트

- **수수료 계산**: Strategy 패턴 (`FeeCalculationStrategy` 인터페이스 → `PgFeeStrategy`, `PlatformFeeStrategy`)
- **수수료 VO**: `@Embeddable FeeDetail` (PG 수수료, 플랫폼 수수료, 총 수수료)
- **정산 배치**: Spring Batch Chunk — `JpaPagingItemReader` → `ItemProcessor` → `ItemWriter`. chunk 처리 후 `EntityManager.clear()`로 OOM 방지
- **정산 조회**: QueryDSL 동적 쿼리 + Redis Cache Aside (확정 데이터 TTL 1시간, 진행 중 5분)
- **복합 인덱스**: `(seller_id, settlement_date, status)`
- **동시성 제어**: `@Version` 낙관적 락 + `@Lock(PESSIMISTIC_WRITE)` 비관적 락 + Spring Retry (3회 재시도)
- **비동기 처리**: `@Async` + `CompletableFuture` — `AsyncConfig`(ThreadPoolTaskExecutor)로 스레드 풀 관리
- **모니터링**: Spring Actuator + Micrometer + Prometheus + Grafana (`compose.yaml`에 포함). 커스텀 메트릭 네이밍: `carrot.settle.*`
- **부하 테스트**: k6 스크립트로 주요 API TPS/P95 측정. 결과는 `docs/load-test/` 에 기록
- **N+1 해결**: fetch join 또는 `@BatchSize` — SQL 로그로 전/후 쿼리 수 비교 검증

### 인프라

`compose.yaml`에 PostgreSQL과 Redis가 정의되어 있으며, `spring-boot-docker-compose` 의존성으로 `bootRun` 시 자동 기동된다. 테스트 환경은 Testcontainers가 별도 컨테이너를 관리한다.

## GitHub Issue 체크리스트 관리

작업 완료 후 반드시 연관된 GitHub 이슈의 체크리스트를 확인하고, 정상 수행된 항목은 체크로 변경한다. **모든 항목이 완료되면 이슈를 닫는다.**

- **Acceptance Criteria**: Given-When-Then 기준으로 실제 동작 여부를 확인한 뒤 체크
- **Tasks**: 코드/파일이 실제로 존재하거나 동작이 확인된 항목만 체크
- 미완료 항목은 체크하지 않는다
- 모든 AC + Tasks가 체크되면 `gh issue close`로 이슈를 닫고, 완료 근거를 코멘트로 남긴다

```bash
# 이슈 내용 확인
gh issue view [이슈번호] --repo yuhaeni/carrot-settle

# 체크리스트 업데이트
gh issue edit [이슈번호] --repo yuhaeni/carrot-settle --body "[업데이트된 본문]"

# 이슈 닫기 (완료 근거 코멘트 포함)
gh issue close [이슈번호] --repo yuhaeni/carrot-settle --comment "[완료 근거]"
```

## Code Conventions

`docs/code-conventions.md`에 프로젝트 코드 컨벤션이 정의되어 있다. **모든 코드 작성 시 반드시 준수한다.**

주요 규칙 요약:
- **URL**: 소문자 케밥-케이스, 복수형, `/api/v1/` 접두사
- **JSON 필드**: camelCase, 접미사 컨벤션 준수 (`xxxAt`, `xxxAmount`, `xxxStatus` 등)
- **금액**: 엔티티/계산은 `BigDecimal` + `RoundingMode.HALF_UP`, API 응답 직렬화는 `Long`
- **날짜**: 일시는 `OffsetDateTime` + ISO 8601, 날짜만은 `LocalDate`
- **Enum**: UPPER_SNAKE_CASE, `@Enumerated(EnumType.STRING)` 필수, `name`/`description` 필드 필수 추가, getter는 Lombok `@Getter` 사용, 상태 전이가 있는 Enum은 `TRANSITIONS` 맵 + `validateTransitionTo()` 패턴 사용
- **SettlementStatus**: `INCOMPLETED`, `COMPLETED`
- **DTO 이름**: `{Action}{Resource}Request` / `{Resource}Response` / `{Resource}ListResponse`
- **에러 응답**: `{ code, message }` 형식, code는 UPPER_SNAKE_CASE

## Branching & Commit

- 브랜치: `feature/[이슈번호]-[간단-설명-kebab-case]`, 이슈 없는 수정은 `fix/` 또는 `chore/`
- 커밋: Conventional Commits (`feat:`, `fix:`, `chore:` 등), 본문에 `Closes #이슈번호` 포함
- PR: 반드시 `main`으로 직접 푸시 금지. PR 본문은 `.github/PR_TEMPLATE/PULL_REQUEST_TEMPLATE.md` 사용

## Hooks (자동 적용)

`.claude/hooks/`에 pre/post tool use 훅이 설정되어 있다.

- **`dispatcher.sh` (pre-tool-use)**:
  - `application*.yaml` / `application*.properties` 파일 읽기 차단
  - `src/main/resources/db/migration/` 및 `db/changelog/` 하위 마이그레이션 파일 수정 차단 (새 파일 생성으로 대응)
  - Entity/Domain 파일 수정 시 차단 — DB 마이그레이션 파일 생성 필요
  - Controller 파일 수정 시 차단 — 관련 엔티티/서비스/DTO 먼저 읽고 계획 수립 필요
- **`format-files.sh` (post-tool-use)**: Java 파일 저장 시 `google-java-format` 또는 Spotless 자동 포맷팅
- **`security-check.sh`**: 코드에 민감 정보 패턴(`password`, `api_key`, `secret`, AWS 키) 포함 시 차단
