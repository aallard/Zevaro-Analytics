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
            // Duplicate event - log and continue (idempotent)
            log.warn("Duplicate hypothesis event ignored: {}. Error: {}",
                event.hypothesisId(), e.getMessage());
        } catch (DataAccessException e) {
            // Database error - log and rethrow for retry
            log.error("Database error processing hypothesis event: {}. Will retry.",
                event.hypothesisId(), e);
            throw e;
        } catch (Exception e) {
            // Unexpected error - log full stack trace
            log.error("Unexpected error processing hypothesis event: {}",
                event.hypothesisId(), e);
            throw new RuntimeException("Failed to process hypothesis event", e);
        }
    }
}
