package com.monsterhouse.slot;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import com.monsterhouse.slot.error.SlotConflictExceptionMapper;
import com.monsterhouse.slot.health.BookingHealthCheck;
import com.monsterhouse.slot.resources.BookingResource;
import ru.vyarus.dropwizard.guice.GuiceBundle;

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
        bootstrap.addBundle(GuiceBundle.builder()
                // 컴포넌트 스캔을 쓰지 않습니다. enableAutoConfig() 를 부르면 패키지를
                // 훑어 확장을 찾아주지만, 그러면 무엇이 등록됐는지가 다시 코드 밖으로 나갑니다.
                .modules(new BookingModule())
                // 확장도 명시합니다. 이 목록에 없으면 뜨지 않습니다.
                .extensions(BookingResource.class, BookingHealthCheck.class)
                .build());
    }

    @Override
    public void run(BookingConfiguration configuration, Environment environment) {
        // 예외 매퍼는 주입받을 것이 없어 Guice 를 거치지 않고 Jersey 에 직접 등록합니다.
        environment.jersey().register(new SlotConflictExceptionMapper());
    }
}
