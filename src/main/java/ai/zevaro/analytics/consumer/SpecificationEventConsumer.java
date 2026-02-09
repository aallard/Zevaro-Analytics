package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.SpecificationApprovedEvent;
import ai.zevaro.analytics.consumer.events.SpecificationCreatedEvent;
import ai.zevaro.analytics.consumer.events.SpecificationStatusChangedEvent;
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
public class SpecificationEventConsumer {

    private final MetricsService metricsService;

    private final RateLimitedConsumerLogger dbErrorLogger = new RateLimitedConsumerLogger();
    private final RateLimitedConsumerLogger duplicateLogger = new RateLimitedConsumerLogger();

    @KafkaListener(topics = AppConstants.TOPIC_SPECIFICATION_CREATED)
    public void onSpecificationCreated(SpecificationCreatedEvent event) {
        try {
            log.debug("Processing specification created event: {} for tenant {}",
                event.specificationId(), event.tenantId());

            metricsService.recordSpecificationCreated(event);

            log.debug("Successfully processed specification created: {}", event.specificationId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate specification created event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.specificationId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing specification created event(s). Latest: {}. ({} suppressed in last interval)", event.specificationId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing specification created event: {}",
                event.specificationId(), e);
            throw new RuntimeException("Failed to process specification created event", e);
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_SPECIFICATION_STATUS_CHANGED)
    public void onSpecificationStatusChanged(SpecificationStatusChangedEvent event) {
        try {
            log.debug("Processing specification status changed event: {} for tenant {}",
                event.specificationId(), event.tenantId());

            metricsService.recordSpecificationStatusChanged(event);

            log.debug("Successfully processed specification status changed: {}", event.specificationId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate specification status changed event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.specificationId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing specification status changed event(s). Latest: {}. ({} suppressed in last interval)", event.specificationId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing specification status changed event: {}",
                event.specificationId(), e);
            throw new RuntimeException("Failed to process specification status changed event", e);
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_SPECIFICATION_APPROVED)
    public void onSpecificationApproved(SpecificationApprovedEvent event) {
        try {
            log.debug("Processing specification approved event: {} for tenant {}",
                event.specificationId(), event.tenantId());

            metricsService.recordSpecificationApproved(event);

            log.debug("Successfully processed specification approved: {}", event.specificationId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate specification approved event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.specificationId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing specification approved event(s). Latest: {}. ({} suppressed in last interval)", event.specificationId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing specification approved event: {}",
                event.specificationId(), e);
            throw new RuntimeException("Failed to process specification approved event", e);
        }
    }
}
