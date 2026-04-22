ALTER TABLE settlements
    ADD COLUMN total_fee NUMERIC(19, 2) NOT NULL DEFAULT 0;

UPDATE settlements
SET total_fee = pg_fee + platform_fee;
