package boxoffice.tickets;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingConfirmed(UUID eventId, long bookingId, String showId, String seat, String customer) {
}
