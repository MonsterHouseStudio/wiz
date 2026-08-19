package com.monsterhouse.slot;

import com.fasterxml.jackson.databind.SerializationFeature;
import io.dropwizard.core.Application;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import com.monsterhouse.slot.error.SlotConflictExceptionMapper;
import com.monsterhouse.slot.health.BookingHealthCheck;
import com.monsterhouse.slot.resources.BookingResource;
import ru.vyarus.dropwizard.guice.GuiceBundle;
import ru.vyarus.guicey.jdbi3.JdbiBundle;

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
        // 스키마 관리. `java -jar ... db migrate config.yml` 로 실행합니다.
        // Spring Boot 였다면 Flyway 를 클래스패스에 넣는 것만으로 부팅 시 자동 실행됐습니다.
        // 여기서는 명령을 등록해야 생기고, 부팅과 분리되어 있어 배포 시 명시적으로 돌려야 합니다.
        bootstrap.addBundle(new MigrationsBundle<BookingConfiguration>() {
            @Override
            public DataSourceFactory getDataSourceFactory(BookingConfiguration configuration) {
                return configuration.getDatabase();
            }

            // 기본값은 migrations.xml 입니다. 이 이름을 안 바꾸면 파일을 못 찾고
            // 명령이 실패하는데, Application#run 은 실패 시 System.exit(1) 을 부릅니다.
            // 테스트에서 이걸 만나면 JVM 이 통째로 죽어 원인이 안 보입니다.
            @Override
            public String getMigrationsFileName() {
                return "migrations.sql";
            }
        });

        bootstrap.addBundle(GuiceBundle.builder()
                // 컴포넌트 스캔을 쓰지 않습니다. enableAutoConfig() 를 부르면 패키지를
                // 훑어 확장을 찾아주지만, 그러면 무엇이 등록됐는지가 다시 코드 밖으로 나갑니다.
                // JDBI 를 Guice 가 주입할 수 있게 합니다. Jdbi 인스턴스와 트랜잭션 AOP 를 붙여줍니다.
                .bundles(JdbiBundle.<BookingConfiguration>forDatabase(
                        (conf, env) -> conf.getDatabase()))
                .modules(new BookingModule())
                // 확장도 명시합니다. 이 목록에 없으면 뜨지 않습니다.
                .extensions(BookingResource.class, BookingHealthCheck.class)
                .build());
    }

    @Override
    public void run(BookingConfiguration configuration, Environment environment) {
        // Dropwizard 의 ObjectMapper 는 JavaTimeModule 을 등록해 주지만
        // WRITE_DATES_AS_TIMESTAMPS 는 켜둔 채로 둡니다. 그대로 두면 LocalDate 가
        // "2026-09-01" 이 아니라 [2026,9,1] 로 나갑니다. 읽기는 멀쩡해서 요청은 통과하고
        // 응답만 조용히 틀립니다. Spring Boot 는 이걸 기본으로 꺼주지만 여기서는 직접 끕니다.
        environment.getObjectMapper()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 예외 매퍼는 주입받을 것이 없어 Guice 를 거치지 않고 Jersey 에 직접 등록합니다.
        environment.jersey().register(new SlotConflictExceptionMapper());
    }
}
