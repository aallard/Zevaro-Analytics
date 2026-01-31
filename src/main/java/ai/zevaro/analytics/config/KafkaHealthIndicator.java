package ai.zevaro.analytics.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kafka health indicator with rate-limited logging.
 *
 * Features:
 * - Logs FIRST failure immediately
 * - Logs summary every 5 minutes while down
 * - Logs recovery message when connection restored
 * - Tracks consecutive failures and downtime
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private final AtomicReference<Instant> lastFailureLog = new AtomicReference<>(Instant.EPOCH);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<Instant> firstFailure = new AtomicReference<>(null);

    private static final Duration LOG_INTERVAL = Duration.ofMinutes(5);

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000
        ))) {
            adminClient.listTopics().names().get(5, TimeUnit.SECONDS);

            // Recovery - log if we were previously down
            if (firstFailure.get() != null) {
                int totalFailures = failureCount.get();
                Duration downtime = Duration.between(firstFailure.get(), Instant.now());
                log.info("Kafka connection RESTORED after {} failures over {}. Resuming normal operation.",
                    totalFailures, formatDuration(downtime));

                // Reset counters
                firstFailure.set(null);
                failureCount.set(0);
            }

            return Health.up()
                .withDetail("bootstrapServers", bootstrapServers)
                .build();

        } catch (Exception e) {
            return handleFailure(e);
        }
    }

    private Health handleFailure(Exception e) {
        int failures = failureCount.incrementAndGet();
        Instant now = Instant.now();

        // Track first failure time
        firstFailure.compareAndSet(null, now);

        // Rate-limited logging
        Instant lastLog = lastFailureLog.get();
        if (failures == 1) {
            // ALWAYS log first failure immediately
            log.error("Kafka UNAVAILABLE at {}. Circuit breaker OPEN. Events will not be processed. Error: {}",
                bootstrapServers, e.getMessage());
            lastFailureLog.set(now);
        } else if (Duration.between(lastLog, now).compareTo(LOG_INTERVAL) > 0) {
            // Periodic summary every 5 minutes
            Duration downtime = Duration.between(firstFailure.get(), now);
            log.warn("Kafka still UNAVAILABLE. {} health check failures over {}. Next log in 5 minutes.",
                failures, formatDuration(downtime));
            lastFailureLog.set(now);
        }
        // Otherwise: silent - don't flood logs

        return Health.down()
            .withDetail("bootstrapServers", bootstrapServers)
            .withDetail("consecutiveFailures", failures)
            .withDetail("downSince", firstFailure.get())
            .withException(e)
            .build();
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
