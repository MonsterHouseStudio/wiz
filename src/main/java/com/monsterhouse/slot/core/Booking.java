package com.monsterhouse.slot.core;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 확정된 예약 한 건.
 *
 * @param date      촬영 날짜
 * @param startTime 시작 (포함)
 * @param endTime   종료 (제외)
 */
public record Booking(long id, LocalDate date, LocalTime startTime, LocalTime endTime) {

    /**
     * 두 예약이 시간대를 공유하는가.
     *
     * <p>원본 Spring Boot 구현의 JPQL 과 같은 식입니다.
     * <pre>start &lt; :end AND end &gt; :start</pre>
     *
     * <p>등호가 없는 것이 핵심입니다. 14:00 에 끝나는 예약과 14:00 에 시작하는 예약은
     * 겹치지 않습니다. {@code <=} 로 쓰면 붙어 있는 두 건이 충돌로 판정되어
     * 하루에 받을 수 있는 예약이 절반으로 줄어듭니다.
     */
    public boolean overlaps(LocalDate otherDate, LocalTime otherStart, LocalTime otherEnd) {
        return date.equals(otherDate)
                && startTime.isBefore(otherEnd)
                && endTime.isAfter(otherStart);
    }
}
