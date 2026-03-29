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
./gradlew bootRun

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

- **수수료 구조**: PG 3% (고정) + 플랫폼 수수료 셀러 등급별 (일반 5% / 우수 3% / VIP 1%)
- **주문 상태 전이**: `CREATED → PAID → CONFIRMED → SETTLED` / `PAID → REFUNDED`
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
