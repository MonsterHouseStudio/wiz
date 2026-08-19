--liquibase formatted sql

--changeset jaeyoon:1-booking-day-lock
-- 1층. 날짜마다 행 하나. 이 행을 SELECT ... FOR UPDATE 로 잡아
-- 같은 날짜의 요청을 직렬화합니다.
--
-- 별도 테이블을 두는 이유: 아직 예약이 없는 날짜에는 잠글 대상이 없습니다.
-- booking 테이블에 직접 FOR UPDATE 를 걸면 InnoDB 는 존재하지 않는 행에 대해
-- 행 락이 아니라 갭 락을 잡고, 갭 락끼리는 서로 호환되지만 insert intention 락과는
-- 충돌해 데드락이 납니다. 그래서 "반드시 존재하는 행" 을 따로 만듭니다.
CREATE TABLE booking_day_lock (
    lock_date DATE NOT NULL,
    PRIMARY KEY (lock_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

--changeset jaeyoon:2-booking
-- 3층. slot_key 에 UNIQUE.
-- 1·2층을 빠져나간 요청이 있어도 DB 가 마지막으로 막습니다.
-- 취소 시 NULL 로 비우면 같은 슬롯을 다시 팔 수 있습니다 —
-- MySQL 의 UNIQUE 는 NULL 을 중복으로 보지 않습니다.
CREATE TABLE booking (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    booking_date DATE       NOT NULL,
    start_time TIME         NOT NULL,
    end_time   TIME         NOT NULL,
    slot_key   VARCHAR(40)  DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_slot_key (slot_key),
    KEY idx_booking_date (booking_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
