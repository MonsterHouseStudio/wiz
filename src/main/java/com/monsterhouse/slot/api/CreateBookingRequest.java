package com.monsterhouse.slot.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * {@code POST /bookings} 요청 본문.
 *
 * <pre>{"date":"2026-09-01","startTime":"14:00","endTime":"15:00"}</pre>
 *
 * <p>Jakarta Bean Validation 은 Spring Boot 에서 쓰던 것과 같은 규격입니다.
 * 다른 것은 발동 방식입니다. Spring 은 {@code @Valid} 를 붙여야 검증하지만,
 * Dropwizard 는 리소스 파라미터에 {@code @Valid} 를 붙이는 것까지는 같고
 * 실패 시 422 응답 본문을 프레임워크가 만들어 줍니다.
 */
public class CreateBookingRequest {

    @NotNull
    private LocalDate date;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    /**
     * 끝이 시작보다 뒤인지.
     *
     * <p>두 필드의 관계는 필드 애노테이션으로 표현할 수 없어 클래스 수준에서 봅니다.
     * 이걸 서비스에 두면 "잘못된 입력(400)" 과 "겹침(409)" 이 같은 자리에서 섞입니다.
     *
     * <p>null 일 때 true 를 돌려주는 이유: 여기서 false 를 주면 date 만 빠뜨린 요청에도
     * 시간 순서 오류가 함께 붙어 나갑니다. 빈 값은 {@code @NotNull} 이 보고하게 둡니다.
     */
    @AssertTrue(message = "endTime must be after startTime")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isTimeRangeValid() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return endTime.isAfter(startTime);
    }

    @JsonProperty
    public LocalDate getDate() {
        return date;
    }

    @JsonProperty
    public void setDate(LocalDate date) {
        this.date = date;
    }

    @JsonProperty
    public LocalTime getStartTime() {
        return startTime;
    }

    @JsonProperty
    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    @JsonProperty
    public LocalTime getEndTime() {
        return endTime;
    }

    @JsonProperty
    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
}
