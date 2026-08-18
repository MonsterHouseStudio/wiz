package com.monsterhouse.slot.error;

import com.monsterhouse.slot.core.SlotConflictException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@link SlotConflictException} → 409 Conflict.
 *
 * <p>Spring 의 {@code @RestControllerAdvice} 에 대응합니다. 다른 점은 등록 방식입니다.
 * {@code @RestControllerAdvice} 는 스캔되면 자동으로 적용되지만, JAX-RS 의
 * {@code @Provider} 는 애노테이션만으로는 아무 일도 하지 않습니다.
 * Jersey 에 등록해야 동작합니다 — BookingApplication#run 을 보세요.
 */
@Provider
public class SlotConflictExceptionMapper implements ExceptionMapper<SlotConflictException> {

    /** 오류 응답 본문. */
    public record ConflictError(String code, String message) {}

    @Override
    public Response toResponse(SlotConflictException exception) {
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ConflictError("SLOT_CONFLICT", exception.getMessage()))
                .build();
    }
}
