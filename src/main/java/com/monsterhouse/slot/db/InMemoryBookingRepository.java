package com.monsterhouse.slot.db;

import com.monsterhouse.slot.core.Booking;

import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 인메모리 구현체.
 *
 * <p>원본의 1층(booking_day_lock 행 잠금)에 대응하는 것이 여기서는 날짜별 락 객체입니다.
 * 전체를 하나의 락으로 묶으면 서로 다른 날짜의 요청까지 줄을 서게 되므로,
 * 원본이 날짜 단위로 잠근 것과 같은 이유로 여기서도 날짜 단위로 잠급니다.
 *
 * <p>{@code computeIfAbsent} 로 날짜별 락을 만드는 이유는, 두 스레드가 같은 날짜에
 * 동시에 도착했을 때 서로 다른 락 객체를 받으면 잠금이 아무 의미가 없기 때문입니다.
 */
@Singleton
public class InMemoryBookingRepository implements BookingRepository {

    private final ConcurrentHashMap<LocalDate, Object> dayLocks = new ConcurrentHashMap<>();
    private final List<Booking> bookings = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Optional<Booking> insertIfNoOverlap(LocalDate date, LocalTime startTime, LocalTime endTime) {
        Object dayLock = dayLocks.computeIfAbsent(date, d -> new Object());

        synchronized (dayLock) {
            boolean conflict = bookings.stream()
                    .anyMatch(b -> b.overlaps(date, startTime, endTime));
            if (conflict) {
                return Optional.empty();
            }
            Booking saved = new Booking(sequence.incrementAndGet(), date, startTime, endTime);
            bookings.add(saved);
            return Optional.of(saved);
        }
    }

    @Override
    public List<Booking> findByDate(LocalDate date) {
        return bookings.stream().filter(b -> b.date().equals(date)).toList();
    }

    @Override
    public int count() {
        return bookings.size();
    }
}
