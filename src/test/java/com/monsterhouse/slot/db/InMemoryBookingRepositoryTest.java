package com.monsterhouse.slot.db;

/** 인메모리 구현체가 계약을 지키는가. */
class InMemoryBookingRepositoryTest extends BookingRepositoryContract {

    @Override
    protected BookingRepository repository() {
        return new InMemoryBookingRepository();
    }
}
