package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.OutcomeValidatedEvent;
import ai.zevaro.analytics.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutcomeEventConsumer {

    private final MetricsService metricsService;

    @KafkaListener(topics = AppConstants.TOPIC_OUTCOME_VALIDATED)
    public void onOutcomeValidated(OutcomeValidatedEvent event) {
        log.info("Outcome validated: {} for tenant {}", event.outcomeId(), event.tenantId());

        metricsService.recordOutcomeValidated(
            event.tenantId(),
            event.outcomeId(),
            event.createdAt(),
            event.validatedAt()
        );
    }
}
