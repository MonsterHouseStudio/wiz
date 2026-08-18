package com.monsterhouse.slot.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.monsterhouse.slot.core.Booking;

import java.time.LocalDate;
import java.time.LocalTime;

/** {@code POST /bookings} 201 응답 본문. */
public record BookingResponse(
        long id,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.id(), booking.date(), booking.startTime(), booking.endTime());
    }
}
