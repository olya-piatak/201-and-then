package boxoffice.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import boxoffice.support.IntegrationTest;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;

class TicketDeskTest extends IntegrationTest {

    private static final Duration DESK = Duration.ofSeconds(15);

    @Test
    void a_booking_ends_with_exactly_one_ticket() {
        String show = newShow();
        long id = api().body(bookingBody("ada", "H5"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201).extract().jsonPath().getLong("id");

        await().atMost(DESK).untilAsserted(() ->
                assertThat(stored.ticketsForBooking(id)).as("tickets for booking %d", id).isPositive());
        await().atMost(DESK).until(topic::ticketDeskCaughtUp);

        assertThat(stored.ticketsForBooking(id)).isEqualTo(1);
    }

    @Test
    void an_event_handed_to_two_consumers_at_once_issues_one_ticket() throws Exception {
        // A rebalance can give the same event to a second consumer while the
        // first one is still printing. Here both copies arrive together, one
        // on each partition, so each consumer gets one.
        UUID eventId = UUID.randomUUID();
        long bookingId = Math.abs(eventId.getMostSignificantBits() % 1_000_000_000L) + 1_000_000_000L;
        String payload = """
                {"eventId":"%s","type":"BookingConfirmed","bookingId":%d,"showId":"%s","seat":"J9","customer":"ada"}
                """.formatted(eventId, bookingId, newShow());

        RecordMetadata first = topic.deliver(0, String.valueOf(bookingId), payload);
        RecordMetadata second = topic.deliver(1, String.valueOf(bookingId), payload);

        // Both consumers must actually have handled their copy before the
        // ticket count means anything.
        await().atMost(DESK).until(() ->
                topic.committedByTicketDesk(0) > first.offset() && topic.committedByTicketDesk(1) > second.offset());

        assertThat(stored.ticketsForEvent(eventId)).isEqualTo(1);
    }
}
