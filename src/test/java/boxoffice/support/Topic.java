package boxoffice.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestComponent;

/**
 * The booking-events topic as a test sees it: what is on it right now, what
 * the ticket desk has committed, and a way to deliver an event by hand.
 */
@TestComponent
public class Topic {

    private static final String TICKET_DESK = "ticket-desk";

    private final String bootstrap;
    private final String name;
    private final ObjectMapper json = new ObjectMapper();

    public Topic(@Value("${spring.kafka.bootstrap-servers}") String bootstrap,
                 @Value("${boxoffice.topic}") String name) {
        this.bootstrap = bootstrap;
        this.name = name;
    }

    /** Every event on the topic for this booking, read up to the current end of each partition. */
    public List<JsonNode> eventsFor(long bookingId) {
        List<JsonNode> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(readerProps())) {
            List<TopicPartition> partitions = partitions(consumer);
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            while (partitions.stream().anyMatch(p -> consumer.position(p) < end.get(p))) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (String.valueOf(bookingId).equals(record.key())) {
                        found.add(parse(record.value()));
                    }
                }
            }
        }
        return found;
    }

    /** Sends the same payload to the given partition. Returns where it landed. */
    public RecordMetadata deliver(int partition, String key, String payload) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(writerProps())) {
            return producer.send(new ProducerRecord<>(name, partition, key, payload)).get();
        }
    }

    /** The offset the ticket desk has committed for a partition, or -1 if none. */
    public long committedByTicketDesk(int partition) throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
            OffsetAndMetadata committed = admin.listConsumerGroupOffsets(TICKET_DESK)
                    .partitionsToOffsetAndMetadata().get()
                    .get(new TopicPartition(name, partition));
            return committed == null ? -1 : committed.offset();
        }
    }

    /** True when the ticket desk has committed everything currently on the topic. */
    public boolean ticketDeskCaughtUp() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
            var committed = admin.listConsumerGroupOffsets(TICKET_DESK).partitionsToOffsetAndMetadata().get();
            var partitions = admin.describeTopics(List.of(name)).allTopicNames().get().get(name).partitions();
            var latest = admin.listOffsets(partitions.stream().collect(Collectors.toMap(
                    p -> new TopicPartition(name, p.partition()), p -> OffsetSpec.latest()))).all().get();
            return latest.entrySet().stream().allMatch(e -> {
                OffsetAndMetadata c = committed.get(e.getKey());
                long end = e.getValue().offset();
                return end == 0 || (c != null && c.offset() >= end);
            });
        }
    }

    private List<TopicPartition> partitions(KafkaConsumer<String, String> consumer) {
        return consumer.partitionsFor(name).stream()
                .map(info -> new TopicPartition(name, info.partition()))
                .toList();
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException("Not JSON on " + name + ": " + value, e);
        }
    }

    private Properties readerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    private Properties writerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return props;
    }
}
