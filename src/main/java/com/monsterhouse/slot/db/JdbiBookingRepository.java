package com.monsterhouse.slot.db;

import com.monsterhouse.slot.core.Booking;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 구현체. 원본 Spring Boot 구현의 3층 방어를 그대로 옮겼습니다.
 *
 * <p>{@link BookingRepository#insertIfNoOverlap} 계약은 인메모리 구현체와 같습니다.
 * 지키는 방법만 다릅니다.
 *
 * <ol>
 *   <li><b>1층</b> — {@code booking_day_lock} 행에 {@code SELECT ... FOR UPDATE}.
 *       같은 날짜의 요청을 직렬화합니다. 이게 없으면 2층이 무력화됩니다.</li>
 *   <li><b>2층</b> — 겹침 쿼리 {@code start < :end AND end > :start}</li>
 *   <li><b>3층</b> — {@code slot_key} UNIQUE. 1·2층을 빠져나가도 DB 가 막습니다.</li>
 * </ol>
 */
@Singleton
public class JdbiBookingRepository implements BookingRepository {

    private final Jdbi jdbi;

    @Inject
    public JdbiBookingRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public Optional<Booking> insertIfNoOverlap(LocalDate date, LocalTime startTime, LocalTime endTime) {
        // 1층 준비. 반드시 예약 트랜잭션 "밖에서" 먼저 커밋되어야 합니다. 이유는 아래 메서드에.
        ensureDayLockRow(date);

        // 트랜잭션 경계가 곧 원자성 경계입니다. 2·3층이 모두 이 안에서 일어나야 합니다.
        return jdbi.inTransaction(handle -> {

            // 1층. 이미 존재하는 행이므로 갭 락 없이 순수 행 락으로 직렬화됩니다.
            handle.createQuery("SELECT lock_date FROM booking_day_lock WHERE lock_date = :d FOR UPDATE")
                    .bind("d", date)
                    .mapTo(LocalDate.class)
                    .one();

            // 2층. 여기부터는 이 날짜에 대해 나 혼자입니다.
            // 등호가 없는 것이 핵심 — 14:00 종료와 14:00 시작은 겹치지 않습니다.
            boolean overlap = handle.createQuery("""
                            SELECT EXISTS (
                                SELECT 1 FROM booking
                                WHERE booking_date = :d
                                  AND start_time < :end
                                  AND end_time   > :start
                            )
                            """)
                    .bind("d", date)
                    .bind("start", startTime)
                    .bind("end", endTime)
                    .mapTo(Boolean.class)
                    .one();

            if (overlap) {
                return Optional.<Booking>empty();
            }

            // 3층. slot_key 는 "이 슬롯" 을 나타내는 값이라 같은 슬롯이면 같은 문자열이 됩니다.
            String slotKey = "%s|%s".formatted(date, startTime);

            try {
                long id = handle.createUpdate("""
                                INSERT INTO booking (booking_date, start_time, end_time, slot_key)
                                VALUES (:d, :start, :end, :slotKey)
                                """)
                        .bind("d", date)
                        .bind("start", startTime)
                        .bind("end", endTime)
                        .bind("slotKey", slotKey)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long.class)
                        .one();

                return Optional.of(new Booking(id, date, startTime, endTime));

            } catch (UnableToExecuteStatementException e) {
                // 3층이 실제로 발동한 경우. 1·2층이 제 역할을 했다면 여기 오지 않습니다.
                // 와도 500 이 아니라 "겹침" 으로 답해야 합니다.
                if (isDuplicateKey(e)) {
                    return Optional.<Booking>empty();
                }
                throw e;
            }
        });
    }

    /**
     * 날짜 락 행을 예약 트랜잭션과 <b>분리된 짧은 트랜잭션</b>에서 미리 만들어 커밋합니다.
     *
     * <p><b>왜 분리해야 하는가 — 실제로 데드락이 났습니다.</b>
     *
     * <p>처음에는 이 INSERT 를 예약 트랜잭션 안에 두었습니다. 계약 테스트의 동시 20건에서
     * {@code Deadlock found when trying to get lock} 이 터졌습니다.
     * {@code INSERT IGNORE} 는 중복 키를 만나면 그 행에 공유(S) 락을 잡습니다.
     * 20개 트랜잭션이 모두 S 락을 쥔 채 바로 다음 줄에서 {@code FOR UPDATE} 로
     * 배타(X) 락으로 승격하려 하고, 서로가 서로의 S 락이 풀리기를 기다립니다.
     *
     * <p>행이 아예 없을 때는 더 나쁩니다. InnoDB 는 존재하지 않는 행에 {@code FOR UPDATE} 를
     * 걸면 행 락이 아니라 갭 락을 잡습니다. 갭 락끼리는 호환되어 N 개가 동시에 잠글 수 있지만,
     * 각자 INSERT 를 시도하는 순간 insert-intention 락이 남의 갭 락과 충돌합니다.
     *
     * <p>그래서 순서를 뒤집습니다.
     * <ol>
     *   <li>여기서 INSERT IGNORE 후 <b>즉시 커밋</b> → 행이 확실히 존재</li>
     *   <li>예약 트랜잭션은 이미 있는 행에 FOR UPDATE → 갭 락 없이 순수 행 락</li>
     * </ol>
     *
     * <p>절대 하면 안 되는 것: 이 메서드를 부르기 전에 바깥에서 {@code FOR UPDATE} 로
     * 존재 여부를 먼저 확인하는 것. 그 순간 바깥 트랜잭션이 갭 락을 쥐게 되어
     * 이 메서드의 INSERT 가 자기 자신을 기다립니다.
     *
     * <p>원본 Spring 구현은 이걸 별도 빈의 {@code @Transactional(REQUIRES_NEW)} 로 풀었습니다.
     * 별도 빈이어야 했던 이유는 같은 클래스 안에서 호출하면 프록시를 타지 않아서입니다.
     * JDBI 에는 프록시가 없어 핸들을 따로 여는 것으로 끝납니다 — 같은 해법, 더 적은 함정.
     */
    private void ensureDayLockRow(LocalDate date) {
        jdbi.useTransaction(handle ->
                handle.createUpdate("INSERT IGNORE INTO booking_day_lock (lock_date) VALUES (:d)")
                        .bind("d", date)
                        .execute());
    }

    private boolean isDuplicateKey(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Booking> findByDate(LocalDate date) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id, booking_date, start_time, end_time FROM booking
                        WHERE booking_date = :d ORDER BY start_time
                        """)
                .bind("d", date)
                .map((rs, ctx) -> new Booking(
                        rs.getLong("id"),
                        rs.getObject("booking_date", LocalDate.class),
                        rs.getObject("start_time", LocalTime.class),
                        rs.getObject("end_time", LocalTime.class)))
                .list());
    }

    @Override
    public int count() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM booking").mapTo(Integer.class).one());
    }
}
