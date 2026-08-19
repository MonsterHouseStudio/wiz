package com.monsterhouse.slot.db;

import com.monsterhouse.slot.core.Booking;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BookingRepository} 계약 테스트.
 *
 * <p>이 저장소의 주장은 "겹침 방어 설계가 프레임워크와 저장소에 의존하지 않는다" 입니다.
 * 그 주장이 성립하는지의 판단 기준이 이 파일입니다 —
 * <b>인메모리 구현체와 MySQL 구현체가 같은 테스트를 통과해야 합니다.</b>
 *
 * <p>구현체는 서로 완전히 다른 수단을 씁니다. 하나는 날짜별 락 객체와 {@code synchronized},
 * 다른 하나는 {@code SELECT ... FOR UPDATE} 와 UNIQUE 제약입니다.
 * 그런데도 아래 테스트는 한 글자도 다르지 않습니다.
 */
abstract class BookingRepositoryContract {

    /** 매 테스트마다 비어 있는 저장소를 줍니다. */
    protected abstract BookingRepository repository();

    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);

    @Test
    void 겹치면_저장되지_않는다() {
        BookingRepository repo = repository();

        assertThat(repo.insertIfNoOverlap(DATE, LocalTime.of(14, 0), LocalTime.of(15, 0)))
                .isPresent();

        // 14:30~15:30 은 앞 예약의 뒤쪽 30분과 겹칩니다.
        assertThat(repo.insertIfNoOverlap(DATE, LocalTime.of(14, 30), LocalTime.of(15, 30)))
                .isEmpty();

        assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    void 붙어있으면_겹치지_않는다() {
        BookingRepository repo = repository();

        // 겹침 조건에 등호가 들어가면 아래 두 번째가 거절됩니다.
        assertThat(repo.insertIfNoOverlap(DATE, LocalTime.of(10, 0), LocalTime.of(11, 0)))
                .isPresent();
        assertThat(repo.insertIfNoOverlap(DATE, LocalTime.of(11, 0), LocalTime.of(12, 0)))
                .isPresent();

        assertThat(repo.count()).isEqualTo(2);
    }

    @Test
    void 다른_날짜는_서로_영향이_없다() {
        BookingRepository repo = repository();

        assertThat(repo.insertIfNoOverlap(DATE, LocalTime.of(14, 0), LocalTime.of(15, 0)))
                .isPresent();
        assertThat(repo.insertIfNoOverlap(DATE.plusDays(1), LocalTime.of(14, 0), LocalTime.of(15, 0)))
                .isPresent();

        assertThat(repo.findByDate(DATE)).hasSize(1);
    }

    /**
     * 이 저장소가 존재하는 이유.
     *
     * <p>같은 시간대에 20개 요청을 동시에 던져 정확히 1건만 성공해야 합니다.
     * 검사와 저장이 원자적이지 않으면 여러 건이 통과합니다.
     */
    @Test
    void 동시_요청_20건_중_정확히_1건만_성공한다() throws Exception {
        BookingRepository repo = repository();
        int threads = 20;

        // 모든 스레드를 같은 순간에 출발시킵니다. 순차로 던지면 경합이 재현되지 않습니다.
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            List<Callable<Optional<Booking>>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> {
                    startGun.await();
                    return repo.insertIfNoOverlap(DATE, LocalTime.of(14, 0), LocalTime.of(15, 0));
                });
            }

            List<Future<Optional<Booking>>> futures = new java.util.ArrayList<>();
            for (Callable<Optional<Booking>> task : tasks) {
                futures.add(pool.submit(task));
            }
            startGun.countDown();

            int succeeded = 0;
            for (Future<Optional<Booking>> f : futures) {
                if (f.get(60, TimeUnit.SECONDS).isPresent()) {
                    succeeded++;
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(repo.count()).isEqualTo(1);

        } finally {
            pool.shutdownNow();
        }
    }
}
