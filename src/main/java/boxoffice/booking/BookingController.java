package boxoffice.booking;

import java.net.URI;
import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/shows/{showId}/bookings")
    public ResponseEntity<Booking> book(@PathVariable String showId,
                                        @Valid @RequestBody BookingRequest request,
                                        @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        Booking booking = bookings.book(showId, request, key);
        return ResponseEntity.created(URI.create("/bookings/" + booking.id())).body(booking);
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<Booking> get(@PathVariable long id) {
        return ResponseEntity.of(bookings.find(id));
    }

    @ExceptionHandler(SeatTakenException.class)
    public ResponseEntity<Map<String, String>> seatTaken(SeatTakenException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "seat_taken", "message", e.getMessage()));
    }
}
