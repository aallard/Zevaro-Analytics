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

    // Rate-limited loggers — prevent log storms during DB outages or event replays
    private final RateLimitedConsumerLogger dbErrorLogger = new RateLimitedConsumerLogger();
    private final RateLimitedConsumerLogger duplicateLogger = new RateLimitedConsumerLogger();

    @KafkaListener(topics = AppConstants.TOPIC_DECISION_RESOLVED)
    public void onDecisionResolved(DecisionResolvedEvent event) {
        try {
            log.debug("Processing decision resolved event: {} for tenant {}",
                event.decisionId(), event.tenantId());

            metricsService.recordDecisionResolved(
                event.tenantId(),
                event.projectId(),
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
            // Duplicate event — rate-limited to prevent flood during rebalance/replay
            duplicateLogger.warnRateLimited(log,
                "Duplicate decision event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.decisionId());
        } catch (DataAccessException e) {
            // Database error — rate-limited to prevent flood during DB outage
            dbErrorLogger.errorRateLimited(log,
                "Database error processing decision event(s). Latest: {}. ({} suppressed in last interval)", event.decisionId());
            throw e;
        } catch (Exception e) {
            // Unexpected errors are always logged (they should be rare)
            log.error("Unexpected error processing decision event: {}",
                event.decisionId(), e);
            throw new RuntimeException("Failed to process decision event", e);
        }
    }
}
