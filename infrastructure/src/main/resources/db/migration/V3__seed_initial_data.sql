-- =============================================
-- V3: 초기 테스트 데이터 세팅
-- Seller 3명, Product 5개
-- =============================================

INSERT INTO sellers (name, email, grade, created_at, updated_at)
VALUES ('김철수', 'chulsoo@example.com', 'STANDARD', NOW(), NOW()),
       ('이영희', 'younghee@example.com', 'PREMIUM', NOW(), NOW()),
       ('박민준', 'minjun@example.com', 'VIP', NOW(), NOW());

INSERT INTO products (seller_id, name, price, stock, created_at, updated_at)
VALUES ((SELECT id FROM sellers WHERE email = 'chulsoo@example.com'), '무선 이어폰', 29900.00, 100, NOW(), NOW()),
       ((SELECT id FROM sellers WHERE email = 'chulsoo@example.com'), '스마트폰 케이스', 9900.00, 200, NOW(), NOW()),
       ((SELECT id FROM sellers WHERE email = 'younghee@example.com'), '노트북 파우치', 19900.00, 50, NOW(), NOW()),
       ((SELECT id FROM sellers WHERE email = 'younghee@example.com'), '블루투스 키보드', 49900.00, 30, NOW(), NOW()),
       ((SELECT id FROM sellers WHERE email = 'minjun@example.com'), '기계식 키보드', 89900.00, 20, NOW(), NOW());
