package com.monsterhouse.slot;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.monsterhouse.slot.core.BookingService;
import com.monsterhouse.slot.db.BookingRepository;
import com.monsterhouse.slot.db.JdbiBookingRepository;

/**
 * 배선.
 *
 * <p>Spring 의 컴포넌트 스캔은 {@code @Service} 가 붙은 클래스를 알아서 찾아 등록합니다.
 * 편하지만, 무엇이 등록됐는지 알려면 애노테이션을 찾아 코드베이스를 훑어야 합니다.
 * 특히 구현체가 둘 이상일 때 어느 쪽이 주입되는지는 조건부 애노테이션과 설정을
 * 함께 읽어야 알 수 있습니다.
 *
 * <p>여기서는 그 목록이 이 파일 하나입니다. 2단계에서 MySQL 구현체를 붙일 때
 * 바뀌는 곳도 아래 한 줄뿐입니다.
 */
public class BookingModule extends AbstractModule {

    @Override
    protected void configure() {
        // 갈아끼우는 지점.
        //
        // 1단계에서 InMemoryBookingRepository 였던 것이 이 한 줄만 바뀌었습니다.
        // BookingService 도 BookingResource 도 손대지 않았습니다 —
        // "겹치지 않으면 넣는다" 가 저장소의 단일 원자 연산이라
        // 두 구현체가 같은 계약 테스트를 통과하기 때문입니다
        // (BookingRepositoryContract 참고).
        bind(BookingRepository.class).to(JdbiBookingRepository.class).in(Scopes.SINGLETON);

        bind(BookingService.class).in(Scopes.SINGLETON);
    }
}
