package boxoffice.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import boxoffice.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class SeatRaceTest extends IntegrationTest {

    private static final int CUSTOMERS = 10;

    @Test
    void ten_customers_racing_for_one_seat_produce_one_booking() throws Exception {
        String show = newShow();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> answers = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(CUSTOMERS)) {
            for (int i = 0; i < CUSTOMERS; i++) {
                String customer = "fan-" + i;
                answers.add(pool.submit(() -> {
                    start.await();
                    return api().body(bookingBody(customer, "A1"))
                            .post("/shows/{show}/bookings", show)
                            .statusCode();
                }));
            }
            start.countDown();
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> answer : answers) {
            statuses.add(answer.get());
        }

        assertThat(statuses).as("one winner, everyone else told the seat is taken")
                .containsOnlyOnce(201)
                .containsOnly(201, 409);
        assertThat(stored.bookings(show)).as("bookings for seat A1").hasSize(1);
    }
}
