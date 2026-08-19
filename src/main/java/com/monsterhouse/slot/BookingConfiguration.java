package com.monsterhouse.slot;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * config.yml 이 이 클래스로 바인딩됩니다.
 *
 * <p>Spring Boot 의 {@code @ConfigurationProperties} 와 목적은 같지만 두 가지가 다릅니다.
 * 하나, 별도 애노테이션이나 등록이 필요 없습니다 — Application 의 타입 파라미터가 곧 설정 타입입니다.
 * 둘, 검증이 기본입니다. 아래 {@code @NotEmpty} 가 깨지면 부팅이 실패합니다.
 * 잘못된 설정으로 뜬 채 첫 요청에서 죽는 상황이 생기지 않습니다.
 */
public class BookingConfiguration extends Configuration {

    @NotEmpty
    private String serviceName;

    /**
     * 커넥션 풀 설정.
     *
     * <p>Spring Boot 는 {@code spring.datasource.*} 를 읽어 HikariCP 를 알아서 만들어 줍니다.
     * 여기서는 이 필드를 선언하고 {@code JdbiBundle} 에 넘기는 것까지가 제 일입니다.
     */
    @Valid
    @NotNull
    private DataSourceFactory database = new DataSourceFactory();

    @JsonProperty("database")
    public DataSourceFactory getDatabase() {
        return database;
    }

    @JsonProperty("database")
    public void setDatabase(DataSourceFactory database) {
        this.database = database;
    }

    @JsonProperty
    public String getServiceName() {
        return serviceName;
    }

    @JsonProperty
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
