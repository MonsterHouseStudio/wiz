package com.monsterhouse.slot.health;

import com.codahale.metrics.health.HealthCheck;
import com.monsterhouse.slot.db.BookingRepository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * {@code GET http://localhost:8081/healthcheck} 에서 확인합니다.
 *
 * <p>Spring Boot Actuator 는 의존성만 넣으면 엔드포인트가 생기고 기본 지표가 딸려옵니다.
 * Dropwizard 는 관리 포트가 처음부터 별도로 뜨는 대신, 헬스체크는 직접 만들어 등록해야
 * 하나라도 생깁니다. 등록된 것이 없으면 부팅 시 경고를 냅니다.
 *
 * <p>지금은 저장소가 응답하는지만 봅니다. 2단계에서 MySQL 구현체가 들어오면
 * 이 자리가 커넥션 검사로 바뀝니다.
 */
@Singleton
public class BookingHealthCheck extends HealthCheck {

    private final BookingRepository repository;

    @Inject
    public BookingHealthCheck(BookingRepository repository) {
        this.repository = repository;
    }

    @Override
    protected Result check() {
        int count = repository.count();
        return Result.healthy("bookings=%d".formatted(count));
    }
}
