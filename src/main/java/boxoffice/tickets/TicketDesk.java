package boxoffice.tickets;

import com.fasterxml.jackson.databind.ObjectMapper;

import boxoffice.Faults;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Issues one ticket per confirmed booking. Kafka delivers at least once, and a
 * rebalance can hand the same event to two consumers while the first is still
 * printing, so "have I seen this event?" has to be answered by the database.
 */
@Component
public class TicketDesk {

    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final TicketPrinter printer;
    private final ObjectMapper json;
    private final Faults faults;

    public TicketDesk(JdbcTemplate db, TransactionTemplate tx, TicketPrinter printer,
                      ObjectMapper json, Faults faults) {
        this.db = db;
        this.tx = tx;
        this.printer = printer;
        this.json = json;
        this.faults = faults;
    }

    @KafkaListener(topics = "${boxoffice.topic}")
    public void onEvent(String payload) throws Exception {
        BookingConfirmed event = json.readValue(payload, BookingConfirmed.class);
        tx.executeWithoutResult(status -> issue(event));
    }

    private void issue(BookingConfirmed event) {
        if (faults.on("double_ticket")) {
            // Ask, print, then remember. Two consumers can both ask before
            // either remembers.
            Integer seen = db.queryForObject(
                    "select count(*) from processed_events where event_id = ?", Integer.class, event.eventId());
            if (seen != null && seen > 0) {
                return;
            }
            printer.print(event);
            insertTicket(event);
            db.update("insert into processed_events (event_id) values (?) on conflict do nothing", event.eventId());
            return;
        }

        // Claim the event first. A second consumer waits on the row lock and
        // then finds the claim already made.
        int claimed = db.update(
                "insert into processed_events (event_id) values (?) on conflict do nothing", event.eventId());
        if (claimed == 0) {
            return;
        }
        printer.print(event);
        insertTicket(event);
    }

    private void insertTicket(BookingConfirmed event) {
        db.update("insert into tickets (booking_id, event_id) values (?, ?)", event.bookingId(), event.eventId());
    }
}
