package com.monsterhouse.slot;

import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 서버를 띄워 겹침 요청이 409 로 떨어지는지 확인합니다.
 *
 * <p>{@link DropwizardAppExtension} 은 config 를 읽어 Jetty 를 실제로 올립니다.
 * Jersey, Guice 배선, Bean Validation, 예외 매퍼가 모두 실제 경로로 지나갑니다.
 * 서비스만 직접 호출하는 테스트였다면 예외 매퍼가 등록되지 않은 것을 못 잡습니다.
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class BookingConflictIT {

    private static final DropwizardAppExtension<BookingConfiguration> APP =
            new DropwizardAppExtension<>(
                    BookingApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));

    private Response post(Map<String, String> body) {
        Client client = APP.client();
        return client.target("http://localhost:%d/bookings".formatted(APP.getLocalPort()))
                .request()
                .post(Entity.json(body));
    }

    @Test
    void 겹치는_예약은_409() {
        Map<String, String> first =
                Map.of("date", "2026-09-01", "startTime", "14:00", "endTime", "15:00");

        try (Response created = post(first)) {
            assertThat(created.getStatus()).isEqualTo(201);
        }

        // 14:30~15:30 은 앞 예약의 뒤쪽 30분과 겹칩니다.
        Map<String, String> overlapping =
                Map.of("date", "2026-09-01", "startTime", "14:30", "endTime", "15:30");

        try (Response conflict = post(overlapping)) {
            assertThat(conflict.getStatus()).isEqualTo(409);
            assertThat(conflict.readEntity(String.class)).contains("SLOT_CONFLICT");
        }
    }

    @Test
    void 끝이_시작보다_앞이면_422() {
        try (Response response = post(
                Map.of("date", "2026-09-04", "startTime", "16:00", "endTime", "16:00"))) {
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(response.readEntity(String.class)).contains("endTime must be after startTime");
        }
    }

    @Test
    void 빈_값은_NotNull_만_보고한다() {
        // date 를 빠뜨린 요청에 시간 순서 오류까지 함께 붙어 나가면 안 됩니다.
        try (Response response = post(Map.of("startTime", "09:00", "endTime", "10:00"))) {
            assertThat(response.getStatus()).isEqualTo(422);
            String body = response.readEntity(String.class);
            assertThat(body).doesNotContain("endTime must be after startTime");
        }
    }

    @Test
    void 날짜는_ISO_문자열로_나간다() {
        // WRITE_DATES_AS_TIMESTAMPS 를 끄지 않으면 [2026,9,3] 으로 나갑니다.
        // 요청 파싱은 멀쩡해서 통과하고 응답만 조용히 틀리는 종류라 테스트로 고정합니다.
        try (Response created = post(
                Map.of("date", "2026-09-03", "startTime", "09:00", "endTime", "10:00"))) {
            assertThat(created.getStatus()).isEqualTo(201);
            assertThat(created.readEntity(String.class))
                    .contains("\"date\":\"2026-09-03\"")
                    .doesNotContain("[2026,9,3]");
        }
    }

    @Test
    void 붙어있는_예약은_겹치지_않는다() {
        // 앞 예약이 11:00 에 끝나고 뒤 예약이 11:00 에 시작합니다.
        // 겹침 조건에 등호가 들어가면 이 요청이 409 로 잘못 거절됩니다.
        try (Response first = post(
                Map.of("date", "2026-09-02", "startTime", "10:00", "endTime", "11:00"))) {
            assertThat(first.getStatus()).isEqualTo(201);
        }

        try (Response adjacent = post(
                Map.of("date", "2026-09-02", "startTime", "11:00", "endTime", "12:00"))) {
            assertThat(adjacent.getStatus()).isEqualTo(201);
        }
    }
}
