package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.TicketAssignedEvent;
import ai.zevaro.analytics.consumer.events.TicketCreatedEvent;
import ai.zevaro.analytics.consumer.events.TicketResolvedEvent;
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
public class TicketEventConsumer {

    private final MetricsService metricsService;

    private final RateLimitedConsumerLogger dbErrorLogger = new RateLimitedConsumerLogger();
    private final RateLimitedConsumerLogger duplicateLogger = new RateLimitedConsumerLogger();

    @KafkaListener(topics = AppConstants.TOPIC_TICKET_CREATED)
    public void onTicketCreated(TicketCreatedEvent event) {
        try {
            log.debug("Processing ticket created event: {} for tenant {}",
                event.ticketId(), event.tenantId());

            metricsService.recordTicketCreated(event);

            log.debug("Successfully processed ticket created: {}", event.ticketId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate ticket created event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.ticketId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing ticket created event(s). Latest: {}. ({} suppressed in last interval)", event.ticketId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing ticket created event: {}",
                event.ticketId(), e);
            throw new RuntimeException("Failed to process ticket created event", e);
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_TICKET_RESOLVED)
    public void onTicketResolved(TicketResolvedEvent event) {
        try {
            log.debug("Processing ticket resolved event: {} for tenant {}",
                event.ticketId(), event.tenantId());

            metricsService.recordTicketResolved(event);

            log.debug("Successfully processed ticket resolved: {}", event.ticketId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate ticket resolved event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.ticketId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing ticket resolved event(s). Latest: {}. ({} suppressed in last interval)", event.ticketId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing ticket resolved event: {}",
                event.ticketId(), e);
            throw new RuntimeException("Failed to process ticket resolved event", e);
        }
    }

    @KafkaListener(topics = AppConstants.TOPIC_TICKET_ASSIGNED)
    public void onTicketAssigned(TicketAssignedEvent event) {
        try {
            log.debug("Processing ticket assigned event: {} for tenant {}",
                event.ticketId(), event.tenantId());

            metricsService.recordTicketAssigned(event);

            log.debug("Successfully processed ticket assigned: {}", event.ticketId());

        } catch (DataIntegrityViolationException e) {
            duplicateLogger.warnRateLimited(log,
                "Duplicate ticket assigned event(s) ignored. Latest: {}. ({} suppressed in last interval)", event.ticketId());
        } catch (DataAccessException e) {
            dbErrorLogger.errorRateLimited(log,
                "Database error processing ticket assigned event(s). Latest: {}. ({} suppressed in last interval)", event.ticketId());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing ticket assigned event: {}",
                event.ticketId(), e);
            throw new RuntimeException("Failed to process ticket assigned event", e);
        }
    }
}
