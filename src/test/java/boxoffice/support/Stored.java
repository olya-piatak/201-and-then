package boxoffice.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only view of what the service actually wrote. Tests decide from this. */
@TestComponent
public class Stored {

    private final JdbcTemplate db;

    public Stored(JdbcTemplate db) {
        this.db = db;
    }

    public Optional<Map<String, Object>> booking(long id) {
        return db.queryForList("select id, show_id, seat, customer from bookings where id = ?", id)
                .stream().findFirst();
    }

    public List<Map<String, Object>> bookings(String showId) {
        return db.queryForList("select id, show_id, seat, customer from bookings where show_id = ? order by id", showId);
    }

    public Optional<Long> seatHolder(String showId, String seat) {
        return db.queryForList("select booking_id from seat_holds where show_id = ? and seat = ?", Long.class, showId, seat)
                .stream().findFirst();
    }

    public List<Map<String, Object>> outboxFor(long bookingId) {
        return db.queryForList("select event_id, type, payload, published_at from outbox where booking_id = ?", bookingId);
    }

    public int outboxRowsForShow(String showId) {
        return db.queryForObject(
                "select count(*) from outbox o join bookings b on b.id = o.booking_id where b.show_id = ?",
                Integer.class, showId);
    }

    public int ticketsForBooking(long bookingId) {
        return db.queryForObject("select count(*) from tickets where booking_id = ?", Integer.class, bookingId);
    }

    public int ticketsForEvent(UUID eventId) {
        return db.queryForObject("select count(*) from tickets where event_id = ?", Integer.class, eventId);
    }
}
