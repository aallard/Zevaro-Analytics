package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.DecisionResolvedEvent;
import ai.zevaro.analytics.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DecisionEventConsumer {

    private final MetricsService metricsService;

    @KafkaListener(topics = AppConstants.TOPIC_DECISION_RESOLVED)
    public void onDecisionResolved(DecisionResolvedEvent event) {
        log.info("Decision resolved: {} for tenant {}", event.decisionId(), event.tenantId());

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
    }
}
