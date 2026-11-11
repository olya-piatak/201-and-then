package boxoffice.booking;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import boxoffice.Faults;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Service
public class BookingService {

    private final JdbcTemplate db;
    private final PaymentGateway payments;
    private final ObjectMapper json;
    private final Faults faults;

    public BookingService(JdbcTemplate db, PaymentGateway payments, ObjectMapper json, Faults faults) {
        this.db = db;
        this.payments = payments;
        this.json = json;
        this.faults = faults;
    }

    @Transactional
    public Booking book(String showId, BookingRequest request, String idempotencyKey) {
        String key = faults.on("double_submit") ? null : idempotencyKey;
        if (key != null) {
            Optional<Booking> earlier = findByKey(key);
            if (earlier.isPresent()) {
                return earlier.get();
            }
        }

        if (faults.on("seat_race")) {
            // Look first, pay, then write. The seat can be sold to someone else
            // while the card is being charged.
            Integer taken = db.queryForObject(
                    "select count(*) from bookings where show_id = ? and seat = ?",
                    Integer.class, showId, request.seat());
            if (taken != null && taken > 0) {
                throw new SeatTakenException(showId, request.seat());
            }
        } else {
            // Take the seat first. The primary key decides who got it.
            int held = db.update(
                    "insert into seat_holds (show_id, seat) values (?, ?) on conflict do nothing",
                    showId, request.seat());
            if (held == 0) {
                throw new SeatTakenException(showId, request.seat());
            }
        }

        payments.authorize(request.customer());

        Long id = db.queryForObject(
                "insert into bookings (show_id, seat, customer, idempotency_key) values (?, ?, ?, ?) returning id",
                Long.class, showId, request.seat(), request.customer(), key);
        db.update("update seat_holds set booking_id = ? where show_id = ? and seat = ?",
                id, showId, request.seat());
        Booking booking = new Booking(id, showId, request.seat(), request.customer());

        if (!faults.on("lost_event")) {
            writeOutbox(booking);
        }

        if (faults.on("ghost_success")) {
            // The transaction quietly rolls back after the response is built.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return booking;
    }

    public Optional<Booking> find(long id) {
        return one(db.query("select id, show_id, seat, customer from bookings where id = ?",
                (rs, n) -> new Booking(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), id));
    }

    private Optional<Booking> findByKey(String key) {
        return one(db.query("select id, show_id, seat, customer from bookings where idempotency_key = ?",
                (rs, n) -> new Booking(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), key));
    }

    private void writeOutbox(Booking booking) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> event = Map.of(
                "eventId", eventId.toString(),
                "type", "BookingConfirmed",
                "bookingId", booking.id(),
                "showId", booking.showId(),
                "seat", booking.seat(),
                "customer", booking.customer());
        db.update("insert into outbox (event_id, booking_id, type, payload) values (?, ?, ?, ?)",
                eventId, booking.id(), "BookingConfirmed", toJson(event));
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> Optional<T> one(List<T> rows) {
        return rows.stream().findFirst();
    }
}
