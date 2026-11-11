package boxoffice.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        @NotBlank @Size(max = 80) String customer,
        @NotBlank @Pattern(regexp = "[A-Z][0-9]{1,2}", message = "a row letter and a seat number, like C7") String seat) {
}
