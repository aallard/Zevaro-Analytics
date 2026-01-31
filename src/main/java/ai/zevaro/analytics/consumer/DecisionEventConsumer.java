package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.DecisionResolvedEvent;
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
public class DecisionEventConsumer {

    private final MetricsService metricsService;

    @KafkaListener(topics = AppConstants.TOPIC_DECISION_RESOLVED)
    public void onDecisionResolved(DecisionResolvedEvent event) {
        try {
            log.debug("Processing decision resolved event: {} for tenant {}",
                event.decisionId(), event.tenantId());

            metricsService.recordDecisionResolved(
                event.tenantId(),
                event.decisionId(),
                event.createdAt(),
                event.resolvedAt(),
                event.priority(),
                event.decisionType(),
                event.wasEscalated(),
                event.stakeholderId()
            );

            log.debug("Successfully processed decision: {}", event.decisionId());

        } catch (DataIntegrityViolationException e) {
            // Duplicate event - log and continue (idempotent)
            log.warn("Duplicate decision event ignored: {}. Error: {}",
                event.decisionId(), e.getMessage());
        } catch (DataAccessException e) {
            // Database error - log and rethrow for retry
            log.error("Database error processing decision event: {}. Will retry.",
                event.decisionId(), e);
            throw e;
        } catch (Exception e) {
            // Unexpected error - log full stack trace
            log.error("Unexpected error processing decision event: {}",
                event.decisionId(), e);
            throw new RuntimeException("Failed to process decision event", e);
        }
    }
}
