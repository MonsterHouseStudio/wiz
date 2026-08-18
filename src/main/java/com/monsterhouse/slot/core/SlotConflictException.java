package com.monsterhouse.slot.core;

/** 요청한 시간대에 이미 예약이 있을 때. HTTP 409 로 매핑됩니다. */
public class SlotConflictException extends RuntimeException {

    public SlotConflictException(String message) {
        super(message);
    }
}
