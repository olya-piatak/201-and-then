package boxoffice.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import boxoffice.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class BookingEventsTest extends IntegrationTest {

    private static final Duration RELAY = Duration.ofSeconds(10);

    @Test
    void every_booking_puts_one_booking_confirmed_event_on_the_topic() {
        String show = newShow();
        long id = api().body(bookingBody("ada", "E3"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201).extract().jsonPath().getLong("id");

        await().atMost(RELAY).untilAsserted(() ->
                assertThat(stored.outboxFor(id))
                        .as("outbox rows for booking %d", id)
                        .singleElement()
                        .satisfies(row -> assertThat(row.get("published_at")).isNotNull()));

        assertThat(topic.eventsFor(id)).singleElement().satisfies(event -> {
            assertThat(event.get("type").asText()).isEqualTo("BookingConfirmed");
            assertThat(event.get("showId").asText()).isEqualTo(show);
            assertThat(event.get("seat").asText()).isEqualTo("E3");
            assertThat(event.get("customer").asText()).isEqualTo("ada");
        });
    }

    @Test
    void a_retried_request_publishes_no_second_event() {
        String show = newShow();
        String key = UUID.randomUUID().toString();
        long id = api().header("Idempotency-Key", key).body(bookingBody("grace", "F1"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201).extract().jsonPath().getLong("id");
        api().header("Idempotency-Key", key).body(bookingBody("grace", "F1"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201);

        await().atMost(RELAY).untilAsserted(() ->
                assertThat(stored.outboxFor(id)).allSatisfy(row -> assertThat(row.get("published_at")).isNotNull()));

        assertThat(stored.outboxRowsForShow(show)).isEqualTo(1);
        assertThat(topic.eventsFor(id)).hasSize(1);
    }

    @Test
    void a_refused_booking_publishes_nothing() {
        String show = newShow();
        api().body(bookingBody("ada", "G2"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201);
        api().body(bookingBody("linus", "G2"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(409);

        assertThat(stored.outboxRowsForShow(show)).isEqualTo(1);
    }
}
