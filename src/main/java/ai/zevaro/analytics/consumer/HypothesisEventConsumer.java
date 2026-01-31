package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.HypothesisConcludedEvent;
import ai.zevaro.analytics.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HypothesisEventConsumer {

    private final MetricsService metricsService;

    @KafkaListener(topics = AppConstants.TOPIC_HYPOTHESIS_CONCLUDED)
    public void onHypothesisConcluded(HypothesisConcludedEvent event) {
        log.info("Hypothesis concluded: {} for tenant {} - result: {}",
            event.hypothesisId(), event.tenantId(), event.result());

        metricsService.recordHypothesisConcluded(
            event.tenantId(),
            event.hypothesisId(),
            event.outcomeId(),
            event.result(),
            event.createdAt(),
            event.concludedAt()
        );
    }
}
