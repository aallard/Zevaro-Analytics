package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.OutcomeValidatedEvent;
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
public class OutcomeEventConsumer {

    private final MetricsService metricsService;

    private final RateLimitedConsumerLogger dbErrorLogger = new RateLimitedConsumerLogger();
    private final RateLimitedConsumerLogger duplicateLogger = new RateLimitedConsumerLogger();

    @KafkaListener(topics = AppConstants.TOPIC_OUTCOME_VALIDATED)
    public void onOutcomeValidated(OutcomeValidatedEvent event) {
        try {
            log.debug("Processing outcome validated event: {} for tenant {}",
                event.outcomeId(), event.tenantId());

            metricsService.recordOutcomeValidated(
                event.tenantId(),
                event.outcomeId(),
                event.createdAt(),
                event.validatedAt()
            );

            log.debug("Successfully processed outcome: {}", event.outcomeId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate outcome event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.outcomeId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing outcome event(s). Latest: {}. ({} suppressed in last interval)", event.outcomeId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing outcome event: {}",
                event.outcomeId(), e);
            throw new RuntimeException("Failed to process outcome event", e);
        }
    }
}
