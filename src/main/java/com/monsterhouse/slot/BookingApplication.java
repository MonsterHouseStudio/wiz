package com.monsterhouse.slot;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

/**
 * 진입점.
 *
 * <p>Spring Boot 의 {@code @SpringBootApplication} 이 하던 일이 여기서는 두 메서드로 나뉩니다.
 * {@link #initialize} 는 설정을 읽기 전에 번들을 등록하는 자리이고,
 * {@link #run} 은 설정을 읽은 뒤 실제 객체를 환경에 붙이는 자리입니다.
 * 자동 설정이 없으므로 여기에 적지 않은 것은 뜨지 않습니다.
 */
public class BookingApplication extends Application<BookingConfiguration> {

    public static void main(String[] args) throws Exception {
        new BookingApplication().run(args);
    }

    @Override
    public String getName() {
        return "booking-slot";
    }

    @Override
    public void initialize(Bootstrap<BookingConfiguration> bootstrap) {
        // 다음 커밋에서 GuiceBundle 을 여기에 등록합니다.
    }

    @Override
    public void run(BookingConfiguration configuration, Environment environment) {
        // 리소스와 헬스체크는 Guice 배선 커밋에서 붙입니다.
    }
}
