package boxoffice.support;

import java.util.UUID;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * Every test class shares one Postgres, one Kafka and one running service.
 * Tests never clean up after each other; each one books seats on a show of
 * its own and only looks at those.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({Stored.class, Topic.class})
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        Startables.deepStart(POSTGRES, KAFKA).join();
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @LocalServerPort
    int port;

    @Autowired
    protected Stored stored;

    @Autowired
    protected Topic topic;

    @BeforeEach
    void pointRestAssuredAtTheService() {
        RestAssured.port = port;
    }

    protected static String newShow() {
        return "show-" + UUID.randomUUID();
    }

    protected static RequestSpecification api() {
        return RestAssured.given().contentType(ContentType.JSON);
    }

    protected static String bookingBody(String customer, String seat) {
        return "{\"customer\":\"" + customer + "\",\"seat\":\"" + seat + "\"}";
    }
}
