# carrot-settle 도메인 설계서

---

## 판매자 컨텍스트

```
┌──────────────────────────────────┐
│           판매자 컨텍스트          │
│                                  │
│  aggregate root                  │
│  ┌──────────┐                    │
│  │  Seller  │                    │
│  └──────────┘                    │
│       ↓                          │
│  value object                    │
│  ┌──────────────┐                │
│  │ SellerGrade  │                │
│  │(NORMAL/      │                │
│  │ PREMIUM/VIP) │                │
└──└──────────────┘────────────────┘
```

- SellerGrade는 독립 생명주기가 없고 Seller의 속성이므로 Entity가 아닌 VO
- 등급 변경 이력을 추적해야 한다면 → SellerGradeHistory Entity 추가 필요 (현재 없음)

---

## 상품 컨텍스트

```
┌───────────────────────────────────────┐
│             상품 컨텍스트               │
│                                       │
│  aggregate root                       │
│  ┌─────────┐                          │
│  │ Product │                          │
│  └─────────┘                          │
│       ↓                               │
│  value object                         │
│  ┌───────┐                            │
│  │ Stock │  ← Redis DECR 원자적 차감  │
│  └───────┘                            │
└───────────────────────────────────────┘
```

- OrderItem은 상품 컨텍스트 소속이 아님 — 주문 생성 시 unitPrice를 스냅샷으로 복사해 Order Context에 귀속
- 재고(Stock)는 2차 개발(동시성 제어) 시 Redis DECR 원자 연산으로 차감

---

## 주문 컨텍스트

```
┌──────────────────────────────────────────────────────────────────┐
│                        주문 컨텍스트                               │
│                                                                  │
│  aggregate root                                                  │
│  ┌─────────────────────────────────────────────┐                 │
│  │  Order                                      │                 │
│  │  - status: PAID → CONFIRMED → SETTLED       │                 │
│  │                    └→ REFUNDED              │                 │
│  │  - confirmedAt: OffsetDateTime (확정 시각)  │                 │
│  │                                             │                 │
│  │  confirm()   ← 구매확정 (PAID → CONFIRMED)  │                 │
│  │  settle()    ← 정산 완료 (CONFIRMED → SETTLED) │              │
│  │  refund()    ← 환불 (PAID → REFUNDED)       │                 │
│  └─────────────────────────────────────────────┘                 │
│       ↓                                                          │
│  entity                                                          │
│  ┌───────────┐                                                   │
│  │ OrderItem │  (productId + unitPrice 스냅샷 보관)              │
│  └───────────┘                                                   │
│                                                                  │
│  value object                                                    │
│  ┌─────────────────────────────────────────────────┐            │
│  │ OrderStatus                                     │            │
│  │ (PAID / CONFIRMED / SETTLED / REFUNDED)         │            │
│  └─────────────────────────────────────────────────┘            │
│                                                                  │
│  domain policy (도메인 정책)                                      │
│  ┌──────────────────────────────────────────────┐                │
│  │  AutoConfirmPolicy                           │                │
│  │  - 자동 확정 대상 판단                         │                │
│  │  - 조건: PAID 상태 + 구매일로부터 N일 경과     │                │
│  │  - N은 설정값으로 관리 (기본 3일)             │                │
│  └──────────────────────────────────────────────┘                │
│                                                                  │
│  application service (호출 경로)                                  │
│  ┌──────────────────────────────────────────────┐                │
│  │  OrderConfirmService                         │                │
│  │  - confirmOrder(orderId)   ← 수동 (API)      │                │
│  │  - confirmExpiredOrders()  ← 자동 (@Scheduled, 새벽 02:00) │  │
│  └──────────────────────────────────────────────┘                │
└──────────────────────────────────────────────────────────────────┘
```

- `Order.confirm()`, `Order.settle()`, `Order.refund()` — 상태 전이는 AR에서만 호출 가능 (도메인 로직 일원화)
- `confirmedAt` — confirm() 호출 시점에 기록, 자동/수동 구분 없이 동일하게 저장
- `AutoConfirmPolicy` — 자동 확정 대상 여부를 판단하는 도메인 정책. Application Service가 아닌 도메인 레이어에 위치
- `OrderConfirmService` — 트리거(수동 API / 자동 스케줄러)와 무관하게 `Order.confirm()` 을 호출하는 단일 진입점
- `OrderItem`은 Order 없이 독립 존재 불가 → 같은 Aggregate 내 Entity

---

## 구매확정 흐름

```
┌────────────────────────────────────────────────────────────────┐
│                       구매확정 흐름                              │
│                                                                │
│  트리거 (2가지)                                                  │
│  ┌──────────────────────────┐  ┌──────────────────────────┐   │
│  │  수동 확정                │  │  자동 확정 (스케줄러)      │   │
│  │  PATCH /api/v1/orders    │  │  매일 새벽 02:00          │   │
│  │       /{id}/confirm      │  │  @Scheduled              │   │
│  └──────────┬───────────────┘  └──────────┬───────────────┘   │
│             │                             │                    │
│             └──────────────┬──────────────┘                    │
│                            ↓                                   │
│                   확정 가능 조건 검증                             │
│                   Order.status == PAID                         │
│                   (이미 CONFIRMED/SETTLED/REFUNDED면 예외)       │
│                            ↓                                   │
│                   Order.confirm() 호출                          │
│                   status: PAID → CONFIRMED                     │
│                   confirmedAt 기록                              │
│                            ↓                                   │
│                   정산 배치 대상 편입                             │
│                   (CONFIRMED 주문만 Settlement 집계)             │
└────────────────────────────────────────────────────────────────┘
```

**자동 확정 대상 범위**
- PAID 상태이고 구매일로부터 일정 기간(예: 3일) 경과한 주문을 자동 CONFIRMED 처리
- 기간 기준은 비즈니스 정책에 따라 설정값으로 관리

**도메인 규칙**
- 상태 전이 로직은 반드시 `Order.confirm()` 내부에서만 수행 (Application Service에서 직접 status 변경 금지)
- REFUNDED 주문은 확정 불가
- confirm 후에는 취소/환불 불가 (REFUND는 PAID 상태에서만 가능)

**구매확정 → 정산 연결**
```
Order (CONFIRMED)
      ↓  정산 배치 (@Scheduled 또는 수동 트리거)
Settlement 생성
      └── FeeDetail 계산 (PG 3% + 플랫폼 등급별)
      └── Payout 생성 (PENDING)
      ↓
Order.status → SETTLED
```

---

## 결제 컨텍스트 (2차 7주차 추가 예정)

```
┌──────────────────────────────────────────────────────────┐
│                    결제 컨텍스트                            │
│                                                          │
│  aggregate root                                          │
│  ┌─────────┐                                             │
│  │ Payment │  상태: PENDING → PAID / FAILED / UNKNOWN   │
│  └─────────┘                                             │
│       ↓                                                  │
│  entity                    value object                  │
│  ┌────────────────┐        ┌─────────────────┐           │
│  │ PaymentHistory │        │ IdempotencyKey  │           │
│  │ (시도 이력)     │        │ (UUID 중복 방지) │           │
│  └────────────────┘        └─────────────────┘           │
│                                                          │
│  interface                                               │
│  ┌──────────────────────────────────────┐               │
│  │ PaymentGateway                       │               │
│  │ (approve / cancel / status)          │               │
│  │ → FakePaymentGateway 구현체 (MVP)    │               │
│  └──────────────────────────────────────┘               │
└──────────────────────────────────────────────────────────┘
```

- PaymentHistory가 필요한 이유: 타임아웃 → UNKNOWN 상태 → 재조회 시 이전 시도 이력 추적
- 카카오페이 이미지의 납부 컨텍스트(납부, 납부 BL, 납부수단, 납부 취소)와 대응되는 구조

---

## 정산 컨텍스트

```
┌──────────────────────────────────────────────────────────┐
│                    정산 컨텍스트                            │
│                                                          │
│  aggregate root                                          │
│  ┌────────────┐                                          │
│  │ Settlement │  상태: INCOMPLETED → COMPLETED           │
│  └────────────┘                                          │
│       ↓                                                  │
│  entity                    value object                  │
│  ┌────────┐                ┌───────────────────────┐     │
│  │ Payout │                │       Fee Detail       │     │
│  │ PENDING│                │ pgFee      (3% 고정)  │     │
│  │   ↓    │                │ platformFee(등급별)   │     │
│  │CONFIRM │                │ totalFee   (합계)     │     │
│  │   ↓    │                └───────────────────────┘     │
│  │PAID_OUT│                                              │
│  │ FAILED │                                              │
│  └────────┘                                              │
└──────────────────────────────────────────────────────────┘
```

- Settlement vs Payout 분리 근거: 카카오페이 이미지의 납부 컨텍스트처럼 집계(얼마를 줘야 하는가)와 지급 실행(실제로 송금했는가)은 라이프사이클이 다름. Payout row = 시도 1회 → 실패 이력 자동 추적
- FeeDetail은 현재 Settlement 컬럼에 흩어진 상태 → @Embeddable로 묶는 것이 DDD VO 원칙

---

## 전체 Context Map 요약

```
┌─────────────────────────────────────────────────────────────────┐
│  판매자 컨텍스트      상품 컨텍스트         주문 컨텍스트            │
│  AR: Seller          AR: Product          AR: Order             │
│  VO: SellerGrade     VO: Stock            Entity: OrderItem     │
│       │                   │                    │                │
│       │ sellerId           │ productId          │ orderId        │
│       │                   └────────────────────┘                │
│       │                                        │                │
│       │              결제 컨텍스트 ←────────────┘                │
│       │              AR: Payment                                │
│       │              Entity: PaymentHistory                     │
│       │              VO: IdempotencyKey                         │
│       │                                                         │
│       └──────────────────────────────────────────────────┐      │
│                                                          ↓      │
│                              정산 컨텍스트                        │
│                              AR: Settlement                     │
│                              Entity: Payout                     │
│                              VO: FeeDetail                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 카카오페이 이미지와의 구조 대응

| 카카오페이 (여신) | carrot-settle (정산) | 대응 근거 |
|---|---|---|
| 가입·회원 컨텍스트 | 판매자 컨텍스트 | 서비스 이용 주체 관리 |
| 심사·승인 컨텍스트 | — | 여신 전용 (carrot-settle 범위 외) |
| 한도 컨텍스트 | 상품 컨텍스트 (Stock VO) | 가용 한도/재고 관리 |
| 납부 컨텍스트 | 결제 컨텍스트 + 정산 컨텍스트 | 실제 금전 이동 처리 |
| 청구 컨텍스트 | 정산 컨텍스트 (Settlement) | 금액 확정 및 집계 |
| 연체 컨텍스트 | — (환불 REFUNDED 상태로 단순화) | 여신 전용 |

> 카카오페이는 연체이자 Snapshot, 계좌 Snapshot처럼 이력성 스냅샷 Entity를 별도로 두는 패턴을 씁니다.
> carrot-settle에서는 Payout row 자체가 시도 이력이 되고, OrderItem.unitPrice가 가격 스냅샷 역할을 합니다. 같은 원칙이 적용된 구조입니다.
