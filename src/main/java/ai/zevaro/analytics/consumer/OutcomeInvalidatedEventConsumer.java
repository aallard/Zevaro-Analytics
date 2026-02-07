package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.OutcomeInvalidatedEvent;
import ai.zevaro.analytics.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutcomeInvalidatedEventConsumer {

    private final MetricsService metricsService;

    @KafkaListener(topics = AppConstants.TOPIC_OUTCOME_INVALIDATED)
    public void onOutcomeInvalidated(OutcomeInvalidatedEvent event) {
        try {
            log.debug("Processing outcome invalidated event: {} for tenant {}",
                event.outcomeId(), event.tenantId());

            metricsService.recordOutcomeInvalidated(
                event.tenantId(),
                event.outcomeId(),
                event.createdAt(),
                event.invalidatedAt()
            );

            log.debug("Successfully processed outcome invalidation: {}", event.outcomeId());

        } catch (DataIntegrityViolationException e) {
            // Duplicate event - log and continue (idempotent)
            log.warn("Duplicate outcome invalidated event ignored: {}. Error: {}",
                event.outcomeId(), e.getMessage());
        } catch (DataAccessException e) {
            // Database error - log and rethrow for retry
            log.error("Database error processing outcome invalidated event: {}. Will retry.",
                event.outcomeId(), e);
            throw e;
        } catch (Exception e) {
            // Unexpected error - log full stack trace
            log.error("Unexpected error processing outcome invalidated event: {}",
                event.outcomeId(), e);
            throw new RuntimeException("Failed to process outcome invalidated event", e);
        }
    }
}
