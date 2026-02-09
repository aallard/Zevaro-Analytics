package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.ProgramCreatedEvent;
import ai.zevaro.analytics.consumer.events.ProgramStatusChangedEvent;
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
public class ProgramEventConsumer {

    private final MetricsService metricsService;

    private final RateLimitedConsumerLogger dbErrorLogger = new RateLimitedConsumerLogger();
    private final RateLimitedConsumerLogger duplicateLogger = new RateLimitedConsumerLogger();

    @KafkaListener(topics = AppConstants.TOPIC_PROGRAM_CREATED)
    public void onProgramCreated(ProgramCreatedEvent event) {
        try {
            log.debug("Processing program created event: {} for tenant {}",
                event.programId(), event.tenantId());

            metricsService.recordProgramCreated(event);

            log.debug("Successfully processed program created: {}", event.programId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate program created event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.programId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing program created event(s). Latest: {}. ({} suppressed in last interval)", event.programId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing program created event: {}",
                event.programId(), e);
            throw new RuntimeException("Failed to process program created event", e);
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_PROGRAM_STATUS_CHANGED)
    public void onProgramStatusChanged(ProgramStatusChangedEvent event) {
        try {
            log.debug("Processing program status changed event: {} for tenant {}",
                event.programId(), event.tenantId());

            metricsService.recordProgramStatusChanged(event);

            log.debug("Successfully processed program status changed: {}", event.programId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate program status changed event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.programId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing program status changed event(s). Latest: {}. ({} suppressed in last interval)", event.programId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing program status changed event: {}",
                event.programId(), e);
            throw new RuntimeException("Failed to process program status changed event", e);
        }
    }
}
