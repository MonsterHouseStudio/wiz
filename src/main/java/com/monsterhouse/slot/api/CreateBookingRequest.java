package com.monsterhouse.slot.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * {@code POST /bookings} 요청 본문.
 *
 * <pre>{"date":"2026-09-01","startTime":"14:00","endTime":"15:00"}</pre>
 *
 * <p>Jakarta Bean Validation 은 Spring Boot 에서 쓰던 것과 같은 규격이라 그대로 옮겨집니다.
 * 검증 실패 시 Dropwizard 는 422 를, Spring 은 400 을 냅니다.
 */
public record CreateBookingRequest(
        @NotNull LocalDate date,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime) {

    /**
     * 끝이 시작보다 뒤인지.
     *
     * <p>두 필드의 관계는 필드 애노테이션으로 표현할 수 없어 클래스 수준에서 봅니다.
     * 이걸 서비스에 두면 "잘못된 입력(422)" 과 "겹침(409)" 이 같은 자리에서 섞입니다.
     *
     * <p>null 일 때 true 를 돌려주는 이유: 여기서 false 를 주면 date 만 빠뜨린 요청에도
     * 시간 순서 오류가 함께 붙어 나갑니다. 빈 값은 {@code @NotNull} 이 보고하게 둡니다.
     */
    @AssertTrue(message = "endTime must be after startTime")
    public boolean isTimeRangeValid() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return endTime.isAfter(startTime);
    }
}
