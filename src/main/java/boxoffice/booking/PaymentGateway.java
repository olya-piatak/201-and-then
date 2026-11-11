package boxoffice.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A stand-in for the card provider. It always says yes, and it takes a moment
 * to say it, the way the real one does.
 */
@Component
public class PaymentGateway {

    private final long delayMs;

    public PaymentGateway(@Value("${boxoffice.payment-delay-ms:50}") long delayMs) {
        this.delayMs = delayMs;
    }

    public void authorize(String customer) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment interrupted", e);
        }
    }
}
