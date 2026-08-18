package com.monsterhouse.slot.core;

import com.monsterhouse.slot.db.BookingRepository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 예약 접수.
 *
 * <p>이 클래스에 락도 트랜잭션도 없습니다. "겹치지 않으면 넣는다" 가 저장소의
 * 단일 연산이므로, 서비스는 결과를 해석해 예외로 바꾸는 일만 합니다.
 * 저장소가 인메모리든 MySQL 이든 이 코드는 그대로입니다.
 */
@Singleton
public class BookingService {

    private final BookingRepository repository;

    @Inject
    public BookingService(BookingRepository repository) {
        this.repository = repository;
    }

    public Booking book(LocalDate date, LocalTime startTime, LocalTime endTime) {
        return repository.insertIfNoOverlap(date, startTime, endTime)
                .orElseThrow(() -> new SlotConflictException(
                        "이미 예약된 시간대입니다: %s %s~%s".formatted(date, startTime, endTime)));
    }
}
