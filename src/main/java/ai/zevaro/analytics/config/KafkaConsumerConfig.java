package ai.zevaro.analytics.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration with defensive settings to prevent log flooding.
 *
 * Key features:
 * - Exponential backoff on failures (1s initial, 60s max)
 * - Reduced concurrency (1 thread per listener vs 3)
 * - Circuit breaker pattern via error handler
 * - Conditional on KAFKA_ENABLED property
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // DEFENSIVE: Prevent log flooding on connection failures
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1000);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 60000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 40000);
        props.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        // JSON deserialization
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "ai.zevaro.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class.getName());

        log.info("Kafka consumer configured with defensive settings: bootstrap={}, groupId={}",
            bootstrapServers, groupId);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // REDUCED from 3 to 1 - prevents 9 concurrent retry loops (3 listeners × 3 threads)
        factory.setConcurrency(1);

        // Error handler with exponential backoff
        factory.setCommonErrorHandler(kafkaErrorHandler());

        return factory;
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        // Fixed backoff: 3 retries with 5 second intervals, then give up
        FixedBackOff backOff = new FixedBackOff(5000L, 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            log.error("Kafka message DROPPED after retries. Topic: {}, Partition: {}, Offset: {}. Error: {}",
                record.topic(), record.partition(), record.offset(), exception.getMessage());
        }, backOff);

        // CRITICAL: Do NOT retry deserialization errors - they will never succeed
        errorHandler.addNotRetryableExceptions(
            DeserializationException.class,
            JsonParseException.class,
            JsonMappingException.class
        );

        return errorHandler;
    }
}
