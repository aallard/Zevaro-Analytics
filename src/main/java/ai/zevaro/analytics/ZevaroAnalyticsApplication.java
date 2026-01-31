package ai.zevaro.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableScheduling
public class ZevaroAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZevaroAnalyticsApplication.class, args);
    }
}
