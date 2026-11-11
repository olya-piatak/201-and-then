package boxoffice.tickets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Renders the ticket PDF. Slow, like every PDF library. */
@Component
public class TicketPrinter {

    private final long delayMs;

    public TicketPrinter(@Value("${boxoffice.print-delay-ms:300}") long delayMs) {
        this.delayMs = delayMs;
    }

    public void print(BookingConfirmed event) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Printing interrupted", e);
        }
    }
}
