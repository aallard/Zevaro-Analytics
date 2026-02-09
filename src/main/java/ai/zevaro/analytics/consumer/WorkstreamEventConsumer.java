package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.WorkstreamCreatedEvent;
import ai.zevaro.analytics.consumer.events.WorkstreamStatusChangedEvent;
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
public class WorkstreamEventConsumer {

    private final MetricsService metricsService;

    private final RateLimitedConsumerLogger dbErrorLogger = new RateLimitedConsumerLogger();
    private final RateLimitedConsumerLogger duplicateLogger = new RateLimitedConsumerLogger();

    @KafkaListener(topics = AppConstants.TOPIC_WORKSTREAM_CREATED)
    public void onWorkstreamCreated(WorkstreamCreatedEvent event) {
        try {
            log.debug("Processing workstream created event: {} for tenant {}",
                event.workstreamId(), event.tenantId());

            metricsService.recordWorkstreamCreated(event);

            log.debug("Successfully processed workstream created: {}", event.workstreamId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate workstream created event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.workstreamId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing workstream created event(s). Latest: {}. ({} suppressed in last interval)", event.workstreamId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing workstream created event: {}",
                event.workstreamId(), e);
            throw new RuntimeException("Failed to process workstream created event", e);
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_WORKSTREAM_STATUS_CHANGED)
    public void onWorkstreamStatusChanged(WorkstreamStatusChangedEvent event) {
        try {
            log.debug("Processing workstream status changed event: {} for tenant {}",
                event.workstreamId(), event.tenantId());

            metricsService.recordWorkstreamStatusChanged(event);

            log.debug("Successfully processed workstream status changed: {}", event.workstreamId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate workstream status changed event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.workstreamId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing workstream status changed event(s). Latest: {}. ({} suppressed in last interval)", event.workstreamId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing workstream status changed event: {}",
                event.workstreamId(), e);
            throw new RuntimeException("Failed to process workstream status changed event", e);
        }
    }
}
