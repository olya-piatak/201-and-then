package boxoffice.booking;

public class SeatTakenException extends RuntimeException {

    public SeatTakenException(String showId, String seat) {
        super("Seat " + seat + " for " + showId + " is taken");
    }
}
