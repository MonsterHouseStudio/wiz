package com.monsterhouse.slot.db;

import com.monsterhouse.slot.core.Booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 예약 저장소.
 *
 * <p>이 인터페이스에 메서드가 하나뿐인 것이 이 저장소의 설계 요지입니다.
 *
 * <p>겹침 검사와 저장을 {@code existsOverlap()} + {@code save()} 두 개로 쪼개면,
 * 그 사이가 경합 구간이 됩니다. 두 요청이 동시에 검사를 통과한 뒤 둘 다 저장하는
 * 상황을 막으려면 호출자가 락을 잡아야 하고, 그러면 "어디까지가 원자적인지" 가
 * 구현체가 아니라 호출자 쪽 지식이 됩니다. 구현체를 갈아끼울 때마다
 * 서비스 코드를 다시 검토해야 한다는 뜻입니다.
 *
 * <p>그래서 원자성 경계를 인터페이스에 못박습니다. "겹치지 않으면 넣는다" 가 한 번에
 * 일어나는 하나의 연산입니다. 이 계약을 어떻게 지킬지는 구현체가 정합니다.
 * <ul>
 *   <li>{@link InMemoryBookingRepository} — 날짜별 락</li>
 *   <li>(2단계) JDBI + MySQL — booking_day_lock 행 SELECT ... FOR UPDATE,
 *       겹침 쿼리, slot_key UNIQUE 의 3층</li>
 * </ul>
 *
 * <p>같은 계약을 서로 다른 메커니즘으로 지키는 것이므로, 서비스 코드는 어느 쪽이
 * 꽂히든 바뀌지 않습니다.
 */
public interface BookingRepository {

    /**
     * 겹치는 예약이 없을 때만 저장합니다. 검사와 저장은 원자적입니다.
     *
     * @return 저장된 예약. 겹쳐서 저장하지 못했으면 비어 있음
     */
    java.util.Optional<Booking> insertIfNoOverlap(LocalDate date, LocalTime startTime, LocalTime endTime);

    /** 특정 날짜의 예약 목록. 테스트와 헬스체크용입니다. */
    List<Booking> findByDate(LocalDate date);

    /** 저장된 전체 예약 수. */
    int count();
}
