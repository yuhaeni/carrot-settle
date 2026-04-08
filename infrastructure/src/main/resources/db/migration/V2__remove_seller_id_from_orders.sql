-- =============================================
-- V2: orders 테이블에서 seller_id 컬럼 제거
-- PRD 도메인 모델 기준: Seller는 Order → OrderItem → Product → Seller 체인으로 접근
-- =============================================

ALTER TABLE orders
    DROP COLUMN seller_id;
