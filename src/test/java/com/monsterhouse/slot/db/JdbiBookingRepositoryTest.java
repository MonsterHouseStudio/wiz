package com.monsterhouse.slot.db;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL 구현체가 <b>같은 계약</b>을 지키는가.
 *
 * <p>H2 를 쓰지 않습니다. 이 테스트가 확인하려는 것이 {@code SELECT ... FOR UPDATE} 의
 * 직렬화와 UNIQUE 제약의 동작인데, 임베디드 DB 에서는 InnoDB 의 잠금 동작이
 * 그대로 재현되지 않아 통과해도 아무것도 증명하지 못합니다.
 *
 * <p>원본 운영 환경과 같은 MySQL 8.0 을 씁니다.
 */
@Testcontainers
class JdbiBookingRepositoryTest extends BookingRepositoryContract {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.46")
                    .withDatabaseName("booking")
                    .withUsername("booking")
                    .withPassword("booking");

    private Jdbi jdbi;

    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());

        // 스키마는 운영과 같은 migrations.sql 을 씁니다.
        // 매 테스트마다 비우고 다시 만듭니다 — 앞 테스트가 남긴 행이 다음 테스트의 결과를 바꿉니다.
        jdbi.useHandle(handle -> {
            handle.execute("DROP TABLE IF EXISTS booking");
            handle.execute("DROP TABLE IF EXISTS booking_day_lock");
            handle.execute("""
                    CREATE TABLE booking_day_lock (
                        lock_date DATE NOT NULL,
                        PRIMARY KEY (lock_date)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
                    """);
            handle.execute("""
                    CREATE TABLE booking (
                        id           BIGINT      NOT NULL AUTO_INCREMENT,
                        booking_date DATE        NOT NULL,
                        start_time   TIME        NOT NULL,
                        end_time     TIME        NOT NULL,
                        slot_key     VARCHAR(40) DEFAULT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_booking_slot_key (slot_key),
                        KEY idx_booking_date (booking_date)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
                    """);
        });
    }

    @Override
    protected BookingRepository repository() {
        return new JdbiBookingRepository(jdbi);
    }
}
