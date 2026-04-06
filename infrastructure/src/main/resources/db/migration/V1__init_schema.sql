-- =============================================
-- V1: 초기 스키마 생성
-- =============================================

CREATE TABLE sellers
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    grade      VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE products
(
    id         BIGSERIAL PRIMARY KEY,
    seller_id  BIGINT         NOT NULL REFERENCES sellers (id),
    name       VARCHAR(255)   NOT NULL,
    price      NUMERIC(19, 2) NOT NULL,
    stock      INTEGER        NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE orders
(
    id           BIGSERIAL PRIMARY KEY,
    seller_id    BIGINT         NOT NULL REFERENCES sellers (id),
    status       VARCHAR(20)    NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    version      BIGINT,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE TABLE order_items
(
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id),
    product_id BIGINT         NOT NULL REFERENCES products (id),
    quantity   INTEGER        NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    subtotal   NUMERIC(19, 2) NOT NULL
);

CREATE TABLE settlements
(
    id              BIGSERIAL PRIMARY KEY,
    seller_id       BIGINT         NOT NULL REFERENCES sellers (id),
    settlement_date DATE           NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    total_amount    NUMERIC(19, 2) NOT NULL,
    pg_fee          NUMERIC(19, 2) NOT NULL,
    platform_fee    NUMERIC(19, 2) NOT NULL,
    net_amount      NUMERIC(19, 2) NOT NULL,
    version         BIGINT,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_settlement_seller_date_status
    ON settlements (seller_id, settlement_date, status);

CREATE TABLE payouts
(
    id            BIGSERIAL PRIMARY KEY,
    settlement_id BIGINT         NOT NULL REFERENCES settlements (id),
    status        VARCHAR(20)    NOT NULL,
    amount        NUMERIC(19, 2) NOT NULL,
    created_at    TIMESTAMP
);
