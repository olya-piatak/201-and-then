package boxoffice.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class TopicConfig {

    // Two partitions, so two ticket desk consumers can work at the same time.
    @Bean
    NewTopic bookingEvents(@Value("${boxoffice.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(2).replicas(1).build();
    }
}
