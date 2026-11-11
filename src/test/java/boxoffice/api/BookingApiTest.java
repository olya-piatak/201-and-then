package boxoffice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.UUID;

import boxoffice.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class BookingApiTest extends IntegrationTest {

    @Test
    void a_booking_is_stored_exactly_as_the_api_reported_it() {
        String show = newShow();

        long id = api().body(bookingBody("ada", "C7"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201)
                .body("seat", equalTo("C7"))
                .extract().jsonPath().getLong("id");

        assertThat(stored.booking(id))
                .as("booking %d was confirmed by the API", id)
                .hasValueSatisfying(row -> {
                    assertThat(row.get("show_id")).isEqualTo(show);
                    assertThat(row.get("seat")).isEqualTo("C7");
                    assertThat(row.get("customer")).isEqualTo("ada");
                });
        assertThat(stored.seatHolder(show, "C7")).contains(id);
    }

    @Test
    void a_retried_request_with_the_same_key_returns_the_same_booking() {
        String show = newShow();
        String key = UUID.randomUUID().toString();

        long first = api().header("Idempotency-Key", key).body(bookingBody("grace", "A1"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201).extract().jsonPath().getLong("id");

        // The response got lost on the way back. The client tries again.
        long second = api().header("Idempotency-Key", key).body(bookingBody("grace", "A1"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201).extract().jsonPath().getLong("id");

        assertThat(second).isEqualTo(first);
        assertThat(stored.bookings(show)).hasSize(1);
    }

    @Test
    void a_taken_seat_is_refused_and_stays_with_its_owner() {
        String show = newShow();
        long first = api().body(bookingBody("ada", "B4"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201).extract().jsonPath().getLong("id");

        api().body(bookingBody("linus", "B4"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(409).body("error", equalTo("seat_taken"));

        assertThat(stored.bookings(show)).singleElement()
                .satisfies(row -> assertThat(row.get("customer")).isEqualTo("ada"));
        assertThat(stored.seatHolder(show, "B4")).contains(first);
    }

    @Test
    void a_request_without_a_valid_seat_is_rejected_and_nothing_is_stored() {
        String show = newShow();

        api().body(bookingBody("ada", "the good one"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(400);
        api().body("{\"seat\":\"A1\"}")
                .post("/shows/{show}/bookings", show)
                .then().statusCode(400);

        assertThat(stored.bookings(show)).isEmpty();
    }

    @Test
    void the_booking_can_be_read_back_through_the_api() {
        String show = newShow();
        long id = api().body(bookingBody("margaret", "D12"))
                .post("/shows/{show}/bookings", show)
                .then().statusCode(201).extract().jsonPath().getLong("id");

        api().get("/bookings/{id}", id)
                .then().statusCode(200)
                .body("customer", equalTo("margaret"))
                .body("seat", equalTo("D12"));
        api().get("/bookings/{id}", Long.MAX_VALUE).then().statusCode(404);
    }
}
