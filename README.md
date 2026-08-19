# booking-slot-dropwizard

예약 겹침 판정을 Dropwizard 5 + Guice 로 구현한 저장소입니다.

---

## 1. 왜 만들었나

같은 요건을 Spring Boot 와 Dropwizard 양쪽에서 구현해 무엇이 달라지는지 확인했습니다.

원본은 실제로 운영 중인 촬영 예약 서비스([MONSTER HOUSE](https://github.com/MonsterHouseStudio/BE))입니다.
거기서 이미 푼 문제를 다시 풉니다. 새 기능을 만드는 게 목적이 아니라,
**동시성 방어 설계가 프레임워크에 딸린 것인지 아닌지**를 코드로 확인하는 게 목적입니다.

결론부터 적으면, 방어의 구조는 그대로 옮겨졌고 바뀐 것은 그 구조를 **어디에 적어야 하는가**였습니다.
Spring 에서는 애노테이션과 자동 설정이 대신 적어주던 것을 Dropwizard 에서는 직접 적습니다.
그리고 프레임워크와 무관하게 남는 문제가 무엇인지도 갈라졌습니다. 4절에 적었습니다.

---

## 2. 요건과 3층 방어

**요건** — 촬영팀이 하나라서 같은 시간대에 두 건을 받을 수 없습니다.
동시에 들어온 두 요청 중 하나만 성공해야 합니다.

원본([MonsterHouseStudio/BE](https://github.com/MonsterHouseStudio/BE))은 세 층으로 막았습니다.

| 층 | 수단 | 막는 것 |
|---|---|---|
| 1 | `booking_day_lock` 행에 `SELECT ... FOR UPDATE` | 같은 날짜 요청을 직렬화. 이게 없으면 2층이 무력화됩니다 |
| 2 | 겹침 쿼리 `start < :end AND end > :start` | 시간대 충돌 판정 |
| 3 | `slot_key` UNIQUE (취소 시 NULL) | 1·2층을 빠져나간 경우의 최후 방어 |

2층 조건에 **등호가 없는 것**이 핵심입니다. 10:00~11:30 과 11:30~13:00 은 겹치지 않습니다.
`<=` 로 쓰면 붙어 있는 두 건이 충돌로 판정되어 하루에 받을 수 있는 예약이 절반으로 줍니다.

1층이 없으면 2층은 통과합니다 — 두 트랜잭션이 동시에 "겹침 없음"을 보고 둘 다 INSERT 합니다.
**검사와 저장 사이가 경합 구간**이라는 것이 이 문제의 본질이고, 프레임워크와 무관합니다.

### 이 저장소에서의 대응

구현체는 둘입니다. 인메모리 쪽에는 1층(DB 행 잠금)과 3층(UNIQUE 제약)에 해당하는
DB 기능이 없습니다. 그래도 **경합 구간을 없앤다는 요건은 같으므로**,
저장소 인터페이스에 원자적 연산 하나만 둡니다.

```java
Optional<Booking> insertIfNoOverlap(LocalDate date, LocalTime start, LocalTime end);
```

`existsOverlap()` + `save()` 두 개로 쪼개지 않은 이유가 이 저장소의 요지입니다.
쪼개면 그 사이가 경합 구간이 되고, 막으려면 **호출자가** 락을 잡아야 합니다.
그러면 "어디까지가 원자적인가" 가 구현체가 아니라 호출자 쪽 지식이 되고,
구현체를 갈아끼울 때마다 서비스 코드를 다시 검토해야 합니다.

원자성 경계를 인터페이스에 못박으면 계약은 하나고 지키는 방법만 달라집니다.

| 구현체 | 계약을 지키는 방법 |
|---|---|
| `InMemoryBookingRepository` | 날짜별 락 객체 + `synchronized` |
| `JdbiBookingRepository` | day lock `FOR UPDATE` → 겹침 쿼리 → `slot_key` UNIQUE |

두 구현체는 `BookingRepositoryContract` 라는 **같은 테스트 클래스**를 상속해 통과합니다.
동시 20건 중 정확히 1건만 성공하는 테스트가 그 안에 있습니다.
구현체를 바꿀 때 실제로 바뀐 것은 `BookingModule` 의 바인딩 한 줄이고,
`BookingService` 와 `BookingResource` 는 손대지 않았습니다.

인메모리에서도 전체를 하나의 락으로 묶지 않고 **날짜 단위로** 잠급니다.
원본이 날짜 단위로 잠근 것과 같은 이유입니다 — 다른 날짜 요청까지 줄 세울 이유가 없습니다.

`BookingService` 에는 락도 트랜잭션도 없습니다. 저장소 결과를 예외로 바꾸는 일만 합니다.

---

## 3. Spring Boot ↔ Dropwizard 대응표

| Spring Boot | Dropwizard | 이 저장소에서의 위치 |
|---|---|---|
| `@RestController` | JAX-RS 리소스 (`@Path`) | `resources/BookingResource` |
| `@Service` + 컴포넌트 스캔 | Guice Module 명시적 바인딩 | `BookingModule` |
| `application.yml` + `@ConfigurationProperties` | Configuration 클래스 + YAML | `BookingConfiguration`, `config.yml` |
| `@RestControllerAdvice` | `ExceptionMapper` + Jersey 등록 | `error/SlotConflictExceptionMapper` |
| Spring Data JPA | JDBI3, SQL 직접 | `db/JdbiBookingRepository` |
| `@Transactional` | `Jdbi#inTransaction` / guicey `@InTransaction` | `db/JdbiBookingRepository` |
| Actuator | admin 포트(8081) + HealthCheck + Metrics | `health/BookingHealthCheck` |
| 자동 설정 | Bundle 명시적 등록 | `BookingApplication#initialize` |
| `spring.datasource.*` | `DataSourceFactory` + `JdbiBundle` | `BookingConfiguration#getDatabase` |
| Flyway 자동 실행 | `MigrationsBundle` + `db migrate` 명령 | `migrations.sql` |

Bean Validation(`@NotNull`, `@Valid`)은 양쪽이 같은 Jakarta 규격이라 그대로 옮겨집니다.
검증 실패 시 Dropwizard 는 422 를, Spring 은 400 을 냅니다.

---

## 4. 자동에서 명시로 바뀌면서 무엇이 보이게 됐는가

옮기기 전에 예상한 것은 "코드가 좀 늘겠지" 였습니다. 실제로 겪은 것은 조금 달랐습니다.
**Spring 이 나 대신 내리고 있던 결정이 무엇인지가 보였습니다.** 네 가지를 겪었습니다.

**하나. 응답이 조용히 틀렸습니다.** `POST /bookings` 가 201 을 주는데 본문이
`{"date":[2026,9,1]}` 였습니다. Dropwizard 의 ObjectMapper 는 `JavaTimeModule` 은 등록해 주지만
`WRITE_DATES_AS_TIMESTAMPS` 는 켜둔 채로 둡니다. 요청 파싱은 멀쩡해서 테스트가 201 만 보면 통과합니다.
확인해 보니 원본 BE 에는 Jackson 날짜 설정이 **한 줄도 없었습니다.** ISO 문자열로 나가고 있던 건
제가 정해서가 아니라 Spring Boot 가 기본으로 꺼줬기 때문이었습니다.
지금까지 제 코드라고 생각한 동작 중 하나가 사실 프레임워크의 기본값이었던 셈입니다.
고치면서 회귀 테스트를 같이 붙였습니다.

**둘. 헬스체크가 이름이 없어서 등록을 거부당했습니다.** `HealthCheck` 를 상속했더니
부팅이 `No installer found for extension` 으로 실패했습니다. Dropwizard 의 헬스체크 레지스트리는
이름을 키로 쓰는데 순수 `HealthCheck` 에는 이름이 없고, guicey 는 클래스명에서 지어내지 않고
거부합니다. `NamedHealthCheck` 로 바꿔 `getName()` 을 직접 줬습니다.
Spring 의 Actuator 는 빈 이름에서 지표 이름을 만들어 줍니다 — 편하지만, 클래스 이름을 바꾸면
지표 이름이 따라 바뀐다는 뜻이기도 합니다. 어느 쪽이 나은지는 상황에 따라 다르지만,
**적어도 Dropwizard 쪽은 그 이름이 코드에 적혀 있습니다.**

**셋. `@Provider` 는 애노테이션만으로 아무 일도 하지 않습니다.**
`@RestControllerAdvice` 는 스캔되면 적용되지만, JAX-RS 의 `@Provider` 는 Jersey 에 등록해야 삽니다.
등록을 빠뜨리면 겹침 요청이 409 가 아니라 500 으로 나갑니다.
그래서 통합 테스트를 서비스 직접 호출이 아니라 **실제로 Jetty 를 띄워** 작성했습니다.
서비스만 부르는 테스트는 이 실수를 절대 못 잡습니다.

**넷. 같은 데드락이 다시 났습니다.** MySQL 구현체를 붙이고 계약 테스트의 동시 20건을 돌리자
`Deadlock found when trying to get lock` 이 터졌습니다. 원본에서 이미 겪고 해결한 것과
같은 데드락입니다.

원인은 `INSERT IGNORE` 로 날짜 락 행을 만든 뒤 **같은 트랜잭션 안에서** 바로
`FOR UPDATE` 를 건 것이었습니다. `INSERT IGNORE` 는 중복 키를 만나면 그 행에 공유(S) 락을
잡는데, 20개 트랜잭션이 모두 S 락을 쥔 채 배타(X) 락으로 승격하려 하면서 서로를 기다립니다.
행이 아예 없을 때는 더 나쁩니다 — InnoDB 는 없는 행에 `FOR UPDATE` 를 걸면 갭 락을 잡고,
갭 락끼리는 호환되지만 각자 INSERT 를 시도하는 순간 insert-intention 락이 남의 갭 락과 충돌합니다.

해법도 원본과 같습니다. 락 행 생성을 **별도 트랜잭션으로 분리해 먼저 커밋**하고,
예약 트랜잭션은 이미 존재하는 행에 순수 행 락만 겁니다.
다만 옮기는 쪽이 조금 더 단순했습니다. Spring 에서는 `@Transactional(REQUIRES_NEW)` 가
프록시를 타야 해서 **별도 빈으로 분리**해야 했습니다 — 같은 클래스 안에서 부르면 조용히 무시됩니다.
JDBI 에는 프록시가 없어 핸들을 하나 더 여는 것으로 끝났습니다. 같은 해법, 함정 하나가 적습니다.

이게 이 저장소에서 가장 중요한 결과라고 생각합니다.
**데드락은 프레임워크가 만든 게 아니라 InnoDB 의 잠금 동작이 만든 것이었습니다.**
Spring 을 걷어내도 그대로 남았고, 해법도 그대로 옮겨졌습니다.

네 가지 모두 "Dropwizard 가 불편하다" 는 이야기가 아닙니다.
**앞의 셋은 Spring 을 쓸 때도 존재하던 결정이고, 다만 제가 내리지 않았을 뿐입니다.
넷째는 애초에 프레임워크의 문제가 아니었습니다.**
겹침 판정과 3층 방어의 구조 자체는 두 프레임워크에서 똑같았습니다 — 옮기면서 바뀐 줄이 없습니다.
바뀐 것은 그 구조를 지탱하는 배선을 누가 적느냐였습니다.

한 가지 덧붙이면, 버전도 그랬습니다. `guicey-bom:8.0.2` 는 Dropwizard **5.0.1** 을 고정하는데
최신 패치는 5.0.2 입니다. Spring Boot 였다면 부모 POM 이 정해준 대로 썼을 조합을,
여기서는 "누가 버전의 주인인가" 를 정해야 했습니다. guicey 가 실제로 검증한 조합을 택했습니다.

---

## 5. 실행 방법

### 테스트

```bash
./gradlew test
```

Docker 가 필요합니다. MySQL 8.0 을 Testcontainers 로 띄웁니다.
H2 를 쓰지 않는 이유는 이 저장소가 확인하려는 것이 `SELECT ... FOR UPDATE` 의 직렬화와
UNIQUE 제약의 동작이기 때문입니다. 임베디드 DB 에서는 InnoDB 의 잠금 동작이 재현되지 않아
통과해도 아무것도 증명하지 못합니다.

### 실행

MySQL 이 필요합니다. `config.yml` 의 `database` 블록을 환경에 맞게 고친 뒤,

```bash
./gradlew installDist

# 스키마 생성. Spring Boot 의 Flyway 와 달리 부팅 시 자동으로 돌지 않습니다.
java -cp "build/install/booking-slot-dropwizard/lib/*" \
  com.monsterhouse.slot.BookingApplication db migrate config.yml

java -cp "build/install/booking-slot-dropwizard/lib/*" \
  com.monsterhouse.slot.BookingApplication server config.yml
```

| 포트 | 용도 |
|---|---|
| 8080 | 애플리케이션 |
| 8081 | admin — `/healthcheck`, `/metrics` |

포트가 물려 있으면 실행 시 덮어쓸 수 있습니다.

```bash
java -Ddw.server.applicationConnectors[0].port=18200 -Ddw.server.adminConnectors[0].port=18201 \
  -cp "build/install/booking-slot-dropwizard/lib/*" \
  com.monsterhouse.slot.BookingApplication server config.yml
```

### 확인

```bash
curl -X POST http://localhost:8080/bookings -H 'Content-Type: application/json' -d '{"date":"2026-09-01","startTime":"14:00","endTime":"15:00"}'
```

```
201  {"id":1,"date":"2026-09-01","startTime":"14:00","endTime":"15:00"}
```

같은 시간대에 겹치는 요청을 다시 보내면,

```bash
curl -X POST http://localhost:8080/bookings -H 'Content-Type: application/json' -d '{"date":"2026-09-01","startTime":"14:30","endTime":"15:30"}'
```

```
409  {"code":"SLOT_CONFLICT","message":"이미 예약된 시간대입니다: 2026-09-01 14:30~15:30"}
```

`15:00~16:00` 은 앞 예약과 붙어 있을 뿐 겹치지 않으므로 201 입니다.

### 스택

Java 21 · Dropwizard 5.0.1 (Jetty 12) · dropwizard-guicey 8.0.2 · Guice 7 · JDBI3 · MySQL 8.0 · Gradle · JUnit 5 · Testcontainers

버전은 `ru.vyarus.guicey:guicey-bom:8.0.2` 한 곳에서만 정합니다.
`build.gradle` 의 의존성에 버전이 적혀 있지 않은 것은 그 때문입니다.

---

## 6. 다음

- 예약 취소 — `slot_key` 를 NULL 로 비워 같은 슬롯을 다시 팔 수 있게. 3층 설계의 나머지 절반입니다
- 부하 실험 — 동시 20건은 정확성 확인이고, 처리량과 락 대기 시간은 아직 측정하지 않았습니다
- guicey `@InTransaction` AOP — 지금은 `Jdbi#inTransaction` 을 직접 씁니다.
  트랜잭션 경계가 저장소 안에 하나뿐이라 AOP 를 쓸 이유가 아직 없습니다

범위 밖으로 둔 것: 인증, 회원가입, 페이지네이션, 관리자 기능, 예약 도메인 확장.
겹침 판정 하나만 다룹니다.
