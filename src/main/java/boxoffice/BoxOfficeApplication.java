package boxoffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BoxOfficeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoxOfficeApplication.class, args);
    }
}
