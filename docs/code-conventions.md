# API 코드 컨벤션

`carrot-settle` 프로젝트에 적용할 컨벤션을 정의한다.

---

## 1. URL / Endpoint 설계

### 규칙

- 경로는 **소문자 케밥-케이스** 사용: `/virtual-accounts`, `/cash-receipts`
- 리소스 단수/복수 일관성 유지: 컬렉션은 복수형 (`/payments`, `/settlements`)
- 리소스 식별자는 경로 변수로: `/payments/{paymentKey}`, `/billing/{billingKey}`
- 버전은 경로 앞에 포함: `/v1/...`
- 동사는 HTTP 메서드로 표현. 예외적으로 행위 기반 하위 리소스는 허용

```
POST   /v1/payments/confirm
POST   /v1/payments/{paymentKey}/cancel
POST   /v1/billing/authorizations/issue
DELETE /v1/billing/{billingKey}
```

### 적용 예시 (carrot-settle)

```
POST   /api/v1/orders
PATCH  /api/v1/orders/{id}/confirm
GET    /api/v1/settlements
POST   /api/v1/settlements/calculate
```

---

## 2. HTTP 메서드

| 용도 | 메서드 |
|------|--------|
| 리소스 생성 | `POST` |
| 전체 조회 | `GET` |
| 부분 수정 (상태 전이 등) | `PATCH` |
| 삭제 / 취소 | `DELETE` 또는 `POST /{id}/cancel` |

- 상태 전이(구매 확정, 취소 등)는 `POST /{id}/action` 또는 `PATCH /{id}` 패턴 사용
- 멱등성이 필요한 요청은 `Idempotency-Key` 헤더 활용 고려

---

## 3. JSON 필드 네이밍

### 기본 규칙

- 모든 JSON 키는 **camelCase**
- 약어도 camelCase 처리: `pgFee`, `vatAmount` (not `PGFee`, `VATAmount`)

### 접미사 컨벤션

| 접미사 | 의미 | 예시 |
|--------|------|------|
| `xxxKey` | 고유 식별 키 | `paymentKey`, `billingKey`, `receiptKey` |
| `xxxAt` | 이벤트 발생 일시 (ISO 8601 datetime) | `approvedAt`, `requestedAt`, `canceledAt` |
| `xxxDate` | 날짜만 (ISO 8601 date) | `settlementDate`, `paidOutDate` |
| `xxxCode` | 코드 값 | `issuerCode`, `acquirerCode`, `countryCode` |
| `xxxAmount` | 금액 (정수, 원 단위) | `totalAmount`, `cancelAmount`, `vatAmount` |
| `xxxStatus` | 상태 열거값 | `paymentStatus`, `settlementStatus` |
| `xxxId` | 외부 연동 ID (내부 PK는 `id`) | `orderId`, `customerId` |

### Boolean 필드 접두사

| 접두사 | 사용 맥락 | 예시 |
|--------|----------|------|
| `is` | 현재 상태/속성 | `isInterestFree`, `isPartialCancelable` |
| `use` | 기능 활성화 여부 | `useEscrow`, `useCardPoint` |
| 없음 | 과거 완료 상태 | `expired`, `canceled` |

---

## 4. 날짜 / 시간 형식

```
# 일시 (datetime): ISO 8601 오프셋 포함
yyyy-MM-dd'T'HH:mm:ss±hh:mm
예) 2024-01-15T14:30:00+09:00

# 날짜 (date)
yyyy-MM-dd
예) 2024-01-15
```

### Java 적용

```java
// DTO 필드
@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
private OffsetDateTime approvedAt;

@JsonFormat(pattern = "yyyy-MM-dd")
private LocalDate settlementDate;
```

```yaml
# application.yml
spring:
  jackson:
    date-format: yyyy-MM-dd'T'HH:mm:ssXXX
    time-zone: Asia/Seoul
    serialization:
      write-dates-as-timestamps: false
```

---

## 5. 금액 처리

- 금액은 **정수(integer)** 사용 — 소수점 없음 (원 단위)
- Java 타입: `BigDecimal` (계산) → `Long` (직렬화/API 응답)
- 반올림: 반드시 `RoundingMode.HALF_UP`

```java
// 계산 시
BigDecimal fee = amount.multiply(FEE_RATE).setScale(0, RoundingMode.HALF_UP);

// DTO 직렬화 시
private Long totalAmount;   // JSON: 15000
private Long pgFee;         // JSON: 450
private Long platformFee;   // JSON: 750
```

---

## 6. 상태 열거값 (Status Enum)

- **UPPER_SNAKE_CASE** 사용
- 의미가 명확한 동사/명사 조합

### 참조 패턴 (토스페이먼츠)

```
# 결제 상태
READY | IN_PROGRESS | WAITING_FOR_DEPOSIT | DONE | CANCELED | PARTIAL_CANCELED | ABORTED | EXPIRED

# 정산 상태
INCOMPLETED | COMPLETED
```

### 적용 예시 (carrot-settle)

```java
public enum OrderStatus {
    CREATED, PAID, CONFIRMED, SETTLED, REFUNDED
}

public enum SettlementStatus {
    INCOMPLETED, COMPLETED
}
```

### name / description 필드 필수 추가

모든 Enum은 `name`(한글 명칭)과 `description`(상태 의미 설명)을 반드시 포함한다.
다른 개발자가 코드만 보고 각 값의 의미를 즉시 파악할 수 있도록 하기 위함이다.

getter는 Lombok `@Getter`를 사용한다. 수동 getter 작성 금지.

```java
import lombok.Getter;

@Getter
public enum OrderStatus {
    PAID("결제 완료", "결제가 완료된 상태. 주문 생성과 동시에 진입하며 구매 확정 대기 중"),
    CONFIRMED("구매 확정", "구매자가 수령을 확정한 상태. 정산 대상으로 전환됨");

    private final String name;
    private final String description;

    OrderStatus(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
```

---

## 7. 페이지네이션

### 오프셋 기반 (일반 목록 조회)

정산 내역 등 고정 범위 조회에 사용.

**요청 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `page` | integer | 페이지 번호 (0-based 또는 1-based 통일) |
| `size` | integer | 페이지 당 건수 (기본값 20, 최대 100) |

**응답 구조**

```json
{
  "content": [...],
  "page": 1,
  "size": 20,
  "totalCount": 150
}
```

### 커서 기반 (대용량 스트리밍 조회)

거래 내역 등 연속 조회에 사용.

**요청 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `cursor` / `startingAfter` | string | 마지막 항목의 커서 값 |
| `limit` | integer | 조회 건수 (최대 100) |

**응답 구조**

```json
{
  "hasNext": true,
  "lastCursor": "cursor_value",
  "data": [...]
}
```

### Java 적용

```java
// 오프셋 페이지네이션 DTO
public record SettlementListResponse(
    List<SettlementResponse> content,
    int page,
    int size,
    long totalCount
) {}

// 커서 페이지네이션 DTO
public record TransactionListResponse(
    boolean hasNext,
    String lastCursor,
    List<TransactionResponse> data
) {}
```

---

## 8. 에러 응답

### 형식

```json
{
  "code": "ALREADY_CANCELED_PAYMENT",
  "message": "이미 취소된 결제입니다."
}
```

### 규칙

- `code`: UPPER_SNAKE_CASE, 도메인 접두사 포함 권장
- `message`: 사람이 읽을 수 있는 한국어 메시지
- HTTP 상태 코드와 에러 코드 일관성 유지

### Java 적용

```java
// 에러 응답 DTO
public record ErrorResponse(String code, String message) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }
}

// 에러 코드 열거형
public enum ErrorCode {
    ORDER_NOT_FOUND("주문을 찾을 수 없습니다."),
    ALREADY_CONFIRMED("이미 확정된 주문입니다."),
    SETTLEMENT_IN_PROGRESS("정산이 진행 중입니다.");

    private final String message;
}

// 전역 예외 핸들러
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handle(BusinessException e) {
        return ResponseEntity
            .status(e.getStatus())
            .body(ErrorResponse.of(e.getErrorCode()));
    }
}
```

---

## 9. 인증 / 보안

토스페이먼츠는 `Basic Auth` 방식: `Authorization: Basic {Base64(secretKey:)}`

carrot-settle 내부 API는 별도 인증 체계를 따르나, **외부 PG 연동 클라이언트 구현 시** 동일 패턴 적용:

```java
@Component
public class TossPaymentsClient {

    private final String encodedKey;

    public TossPaymentsClient(@Value("${toss.secret-key}") String secretKey) {
        this.encodedKey = Base64.getEncoder()
            .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
```

- 시크릿 키는 절대 코드에 하드코딩 금지 — `application.yml` + 환경변수 사용
- 테스트 키와 운영 키 분리: `toss.secret-key.test`, `toss.secret-key.prod`

---

## 10. DTO 네이밍 컨벤션

| 용도 | 이름 패턴 | 예시 |
|------|-----------|------|
| API 요청 | `{Action}{Resource}Request` | `CreateOrderRequest`, `ConfirmOrderRequest` |
| API 응답 | `{Resource}Response` | `OrderResponse`, `SettlementResponse` |
| 목록 응답 | `{Resource}ListResponse` | `SettlementListResponse` |
| 내부 커맨드 | `{Action}{Resource}Command` | `CalculateSettlementCommand` |
| 내부 결과 | `{Resource}Result` | `FeeCalculationResult` |

```java
// 요청
public record CreateOrderRequest(
    Long sellerId,
    List<OrderItemRequest> items
) {}

// 응답
public record OrderResponse(
    Long id,
    String orderId,        // 외부 주문번호
    OrderStatus status,
    Long totalAmount,
    OffsetDateTime createdAt
) {}
```

---

## 11. Spring MVC 매핑 패턴

```java
@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    // 목록 조회 — 오프셋 페이지네이션
    @GetMapping
    public ResponseEntity<SettlementListResponse> getSettlements(
        @RequestParam Long sellerId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) SettlementStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) { ... }

    // 배치 수동 트리거
    @PostMapping("/calculate")
    public ResponseEntity<Void> triggerCalculation(
        @RequestBody CalculateSettlementRequest request
    ) { ... }
}
```

---

## 12. 타입 판별 (Discriminated Union)

결제 수단별로 필드 구성이 다를 때 `type` 필드로 구분:

```java
// 공통 응답에 type 필드 포함
public record PaymentResponse(
    String paymentKey,
    String type,           // "NORMAL" | "BILLING" | "BRANDPAY"
    PaymentStatus status,
    Long totalAmount,
    CardInfo card,         // type=NORMAL 일 때만 non-null
    VirtualAccountInfo virtualAccount  // type=VIRTUAL_ACCOUNT 일 때만 non-null
) {}
```

---

## 요약 체크리스트

- [ ] URL 경로: 소문자 케밥-케이스, 복수형, `/api/v1/` 접두사
- [ ] HTTP 메서드: GET/POST/PATCH/DELETE 의미에 맞게 사용
- [ ] JSON 키: camelCase, 접미사 컨벤션 준수 (`xxxAt`, `xxxAmount` 등)
- [ ] Boolean: `is` / `use` / 없음 접두사 구분
- [ ] 일시: `OffsetDateTime` + ISO 8601, 날짜: `LocalDate`
- [ ] 금액: 계산은 `BigDecimal(HALF_UP)`, 직렬화는 `Long`
- [ ] 상태 열거값: UPPER_SNAKE_CASE
- [ ] 페이지네이션: 고정 범위 → 오프셋, 대용량 → 커서
- [ ] 에러: `{ code, message }` 형식, code는 UPPER_SNAKE_CASE
- [ ] 인증 정보: 환경변수로 관리, 코드에 하드코딩 금지
- [ ] DTO 이름: `{Action}{Resource}Request` / `{Resource}Response`
