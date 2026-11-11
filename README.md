# after-the-200

[![ci](https://github.com/olya-piatak/after-the-200/actions/workflows/ci.yml/badge.svg)](https://github.com/olya-piatak/after-the-200/actions/workflows/ci.yml)

Java tests for what a service does after it has already said yes: the row it
stored, the event it published and the consumer that reacts to that event,
possibly twice, possibly at the same time.

The service is a small box office. A fan books a seat, the booking and its
event are written in one transaction, a relay moves the event to Kafka, and a
ticket desk prints one ticket per booking.

```
POST /shows/{id}/bookings   201         bookings, seat_holds, outbox   <- tests read these
          |
       outbox relay  ----->  Kafka: booking-events (2 partitions)    <- and this
                                   |                |
                             ticket desk #1   ticket desk #2
                                   \                /
                              processed_events, tickets              <- and these
```

## Why

The HTTP response covers half of a booking. The other half happens later, in
another process, and Kafka promises to deliver it at least once, which is a
polite way of saying "sometimes twice".

The bugs I remember from event-driven systems never showed up in the response.
The event that was never published. The retry that turned into a second
record. Two consumers handling the same message during a rebalance, each sure
it was the first. Two requests for the last item, both told yes.

None of those can be seen from the API alone, so these tests check the
database and the topic after every call.

## Fault drills

The service can bring back five real bugs on request. CI runs the suite against
each one and fails if the suite stays green.

```
$ python3 scripts/fault_drills.py

CAUGHT  ghost_success: the API answers 201, then the transaction rolls back and nothing is stored
        BookingApiTest > a booking is stored exactly as the api reported it
        BookingApiTest > a retried request with the same key returns the same booking
        BookingApiTest > a taken seat is refused and stays with its owner
        and 6 more
CAUGHT  lost_event: the booking is stored but its outbox row is not, so no event ever leaves
        BookingEventsTest > a refused booking publishes nothing
        BookingEventsTest > a retried request publishes no second event
        BookingEventsTest > every booking puts one booking confirmed event on the topic
        and 1 more
CAUGHT  double_submit: the idempotency key is ignored, so a retry is told its own seat is taken
        BookingApiTest > a retried request with the same key returns the same booking
        BookingEventsTest > a retried request publishes no second event
CAUGHT  seat_race: the seat is checked, paid for, then written, so two fans both get A1
        BookingApiTest > a booking is stored exactly as the api reported it
        BookingApiTest > a taken seat is refused and stays with its owner
        SeatRaceTest > ten customers racing for one seat produce one booking
CAUGHT  double_ticket: the desk checks for the event before printing and records it after, so two consumers both print
        TicketDeskTest > an event handed to two consumers at once issues one ticket

Every fault was caught.
```

`seat_race` is the one that passes every test written one request at a time.
With the fault on, the service checks the seat is free, charges the card, then
writes the booking. Charging the card takes 50 milliseconds. Ten fans pressing
the button together all find the seat free during those 50 milliseconds. The
fix is boring: take the seat first and let a primary key decide who got it.

`double_ticket` is the same bug one hop later. The ticket desk asks "have I
handled this event?", spends 300 milliseconds rendering a PDF, then writes
down that it has. The test puts the same event on both partitions, so each of
the two consumers gets a copy at the same moment. Two things are checked
before that and after it. Before: the group really is two consumers with one
partition each, because right after start-up one consumer can hold both and
then there is no race at all. After: both consumers have committed their
offsets, because a count of one proves nothing if the second one has not run
yet.

## How it is built

| Layer | Checks | Where |
| --- | --- | --- |
| API | stored row matches the response, idempotent retries, taken seats, validation | `api` |
| Events | one event per booking on the topic, none for retries or refusals | `events` |
| Consumers | one ticket per booking, one ticket when two consumers get the same event | `tickets` |
| Concurrency | ten parallel bookings for one seat give one booking | `concurrency` |

JUnit 5, RestAssured, AssertJ, Awaitility and Testcontainers. Postgres and
Kafka are real, started once per run and shared by every test.

Tests read stored state with plain SQL through `support/Stored.java` and read
the topic through `support/Topic.java`, which consumes up to the current end
of each partition instead of waiting for a quiet moment. Silence is not
evidence.

Tests never clean the database. Each one books on a show of its own and only
looks at that show, so the order of tests does not matter.

Waits use Awaitility with a deadline, never a bare sleep. No retries on failed
tests. A test that passes on the second attempt is a finding.

## Running it

```
mvn test                              # whole suite, needs Docker
mvn test -Ddrills.fault=seat_race     # the suite against one fault
python3 scripts/fault_drills.py       # clean run, then every fault
mvn spring-boot:run                   # the service, needs Postgres and Kafka
```

Java 21, Maven and Docker. Testcontainers pulls `postgres:17-alpine` and
`apache/kafka:3.9.1` on the first run.

To run the service on its own, point it at Postgres and Kafka with
`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` and `KAFKA_BOOTSTRAP`.
Flyway creates the tables.

## About the service

`src/main` is a small stand-in written so the tests have something real to run
against: Spring Boot, JdbcTemplate, Flyway, spring-kafka. It covers the parts
of a booking flow where getting it twice or not at all costs money: seat
holds, idempotency keys, a transactional outbox and an idempotent consumer.

The payment gateway always says yes. The ticket printer prints nothing. Both
take their time, as the real ones do.

MIT licensed. Olya Piatak.
