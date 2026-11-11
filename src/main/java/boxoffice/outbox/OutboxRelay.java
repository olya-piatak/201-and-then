package boxoffice.outbox;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves events from the outbox table to Kafka. If Kafka is down the rows wait,
 * and the booking is still there when it comes back.
 */
@Component
public class OutboxRelay {

    private final JdbcTemplate db;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public OutboxRelay(JdbcTemplate db, KafkaTemplate<String, String> kafka,
                       @Value("${boxoffice.topic}") String topic) {
        this.db = db;
        this.kafka = kafka;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${boxoffice.relay-interval-ms}")
    @Transactional
    public void publishPending() throws Exception {
        var pending = db.queryForList(
                "select id, booking_id, payload from outbox where published_at is null order by id limit 100 for update skip locked");
        for (var row : pending) {
            kafka.send(topic, String.valueOf(row.get("booking_id")), (String) row.get("payload"))
                    .get(10, TimeUnit.SECONDS);
            db.update("update outbox set published_at = now() where id = ?", row.get("id"));
        }
    }
}
