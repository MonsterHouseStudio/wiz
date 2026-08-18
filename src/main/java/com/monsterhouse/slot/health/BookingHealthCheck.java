package com.monsterhouse.slot.health;

import com.monsterhouse.slot.db.BookingRepository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import ru.vyarus.dropwizard.guice.module.installer.feature.health.NamedHealthCheck;

/**
 * {@code GET http://localhost:8081/healthcheck} 에서 확인합니다.
 *
 * <p>Spring Boot Actuator 는 의존성만 넣으면 엔드포인트가 생기고 기본 지표가 딸려옵니다.
 * Dropwizard 는 관리 포트가 처음부터 별도로 뜨는 대신, 헬스체크는 직접 만들어 등록해야
 * 하나라도 생깁니다. 등록된 것이 없으면 부팅 시 경고를 냅니다.
 *
 * <p>지금은 저장소가 응답하는지만 봅니다. 2단계에서 MySQL 구현체가 들어오면
 * 이 자리가 커넥션 검사로 바뀝니다.
 *
 * <p>{@link NamedHealthCheck} 를 상속하는 이유: Dropwizard 의 헬스체크 레지스트리는
 * 이름을 키로 쓰는데 순수 {@code HealthCheck} 에는 이름이 없습니다. guicey 는
 * 클래스명에서 이름을 지어내지 않고 등록을 거부합니다 —
 * "No installer found for extension" 으로 부팅이 실패합니다.
 */
@Singleton
public class BookingHealthCheck extends NamedHealthCheck {

    private final BookingRepository repository;

    @Inject
    public BookingHealthCheck(BookingRepository repository) {
        this.repository = repository;
    }

    @Override
    public String getName() {
        return "bookings";
    }

    @Override
    protected Result check() {
        int count = repository.count();
        return Result.healthy("bookings=%d".formatted(count));
    }
}
