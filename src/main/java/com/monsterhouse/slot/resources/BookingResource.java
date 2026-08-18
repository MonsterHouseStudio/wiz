package com.monsterhouse.slot.resources;

import com.monsterhouse.slot.api.BookingResponse;
import com.monsterhouse.slot.api.CreateBookingRequest;
import com.monsterhouse.slot.core.Booking;
import com.monsterhouse.slot.core.BookingService;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /bookings}
 *
 * <p>Spring 의 {@code @RestController} 에 대응합니다. 눈에 띄는 차이는 두 가지입니다.
 * 하나, 경로와 미디어 타입이 클래스/메서드 애노테이션으로 나뉘어 붙습니다.
 * 둘, 201 과 Location 헤더를 프레임워크가 추측해 주지 않으므로 직접 만듭니다.
 * {@code ResponseEntity.created(...)} 가 하던 일을 여기서는 {@code Response.created(...)} 가 합니다.
 */
@Path("/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookingResource {

    private final BookingService bookingService;

    @Inject
    public BookingResource(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @POST
    public Response create(@Valid CreateBookingRequest request) {
        // 겹치면 SlotConflictException 이 올라가고 SlotConflictExceptionMapper 가 409 로 바꿉니다.
        Booking booking = bookingService.book(
                request.getDate(), request.getStartTime(), request.getEndTime());

        return Response.created(java.net.URI.create("/bookings/" + booking.id()))
                .entity(BookingResponse.from(booking))
                .build();
    }
}
