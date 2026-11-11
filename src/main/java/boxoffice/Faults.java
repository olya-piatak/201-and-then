package boxoffice;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Real bugs the service can be told to bring back, so the test suite can prove
 * it would notice. Off unless DRILLS_FAULT (or -Ddrills.fault) names them.
 */
@Component
public class Faults {

    public static final Set<String> KNOWN = Set.of(
            "ghost_success", "lost_event", "double_submit", "seat_race", "double_ticket");

    private static final Logger log = LoggerFactory.getLogger(Faults.class);

    private final Set<String> on;

    public Faults(@Value("${drills.fault:}") String fault) {
        this.on = Arrays.stream(fault.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        for (String name : on) {
            if (!KNOWN.contains(name)) {
                throw new IllegalArgumentException("Unknown fault: " + name + ". Known: " + KNOWN);
            }
        }
        if (!on.isEmpty()) {
            log.warn("Fault drill: running with {} switched on", on);
        }
    }

    public boolean on(String name) {
        return on.contains(name);
    }
}
