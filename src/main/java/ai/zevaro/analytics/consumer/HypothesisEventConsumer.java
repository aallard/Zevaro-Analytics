package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.HypothesisConcludedEvent;
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
public class HypothesisEventConsumer {

    private final MetricsService metricsService;

    private final RateLimitedConsumerLogger dbErrorLogger = new RateLimitedConsumerLogger();
    private final RateLimitedConsumerLogger duplicateLogger = new RateLimitedConsumerLogger();

    @KafkaListener(topics = AppConstants.TOPIC_HYPOTHESIS_CONCLUDED)
    public void onHypothesisConcluded(HypothesisConcludedEvent event) {
        try {
            log.debug("Processing hypothesis concluded event: {} for tenant {} - result: {}",
                event.hypothesisId(), event.tenantId(), event.result());

            metricsService.recordHypothesisConcluded(
                event.tenantId(),
                event.hypothesisId(),
                event.outcomeId(),
                event.result(),
                event.createdAt(),
                event.concludedAt()
            );

            log.debug("Successfully processed hypothesis: {}", event.hypothesisId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate hypothesis event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.hypothesisId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing hypothesis event(s). Latest: {}. ({} suppressed in last interval)", event.hypothesisId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing hypothesis event: {}",
                event.hypothesisId(), e);
            throw new RuntimeException("Failed to process hypothesis event", e);
        }
    }
}
