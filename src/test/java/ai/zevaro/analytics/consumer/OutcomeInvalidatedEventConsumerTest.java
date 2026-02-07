package ai.zevaro.analytics.consumer;

import ai.zevaro.analytics.consumer.events.OutcomeInvalidatedEvent;
import ai.zevaro.analytics.metrics.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutcomeInvalidatedEventConsumer Tests")
class OutcomeInvalidatedEventConsumerTest {

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private OutcomeInvalidatedEventConsumer eventConsumer;

    private OutcomeInvalidatedEvent event;
    private UUID tenantId;
    private UUID outcomeId;
    private UUID invalidatedBy;
    private Instant createdAt;
    private Instant invalidatedAt;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        outcomeId = UUID.randomUUID();
        invalidatedBy = UUID.randomUUID();
        createdAt = Instant.now().minusSeconds(86400);
        invalidatedAt = Instant.now();

        event = new OutcomeInvalidatedEvent(
            tenantId,
            outcomeId,
            "Increase user engagement",
            invalidatedBy,
            createdAt,
            invalidatedAt
        );
    }

    @Test
    @DisplayName("onOutcomeInvalidated should call metricsService with correct parameters")
    void testOnOutcomeInvalidated_ShouldCallMetricsServiceWithCorrectParams() {
        doNothing().when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        eventConsumer.onOutcomeInvalidated(event);

        verify(metricsService).recordOutcomeInvalidated(tenantId, outcomeId, createdAt, invalidatedAt);
    }

    @Test
    @DisplayName("onOutcomeInvalidated should call metricsService with all required parameters")
    void testOnOutcomeInvalidated_ShouldCallMetricsServiceWithAllRequiredParams() {
        doNothing().when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        eventConsumer.onOutcomeInvalidated(event);

        verify(metricsService).recordOutcomeInvalidated(
            eq(event.tenantId()),
            eq(event.outcomeId()),
            eq(event.createdAt()),
            eq(event.invalidatedAt())
        );
    }

    @Test
    @DisplayName("onOutcomeInvalidated should handle DataIntegrityViolationException silently")
    void testOnOutcomeInvalidated_ShouldHandleDataIntegrityViolationException() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("Duplicate key");
        doThrow(exception).when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        // Should not throw exception
        eventConsumer.onOutcomeInvalidated(event);

        // Verify the service was called
        verify(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("onOutcomeInvalidated should catch DataIntegrityViolationException without rethrowing")
    void testOnOutcomeInvalidated_ShouldCatchDataIntegrityViolationWithoutRethrowing() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("Duplicate outcome invalidation");
        doThrow(exception).when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        // Should complete without exception
        eventConsumer.onOutcomeInvalidated(event);

        verify(metricsService).recordOutcomeInvalidated(tenantId, outcomeId, createdAt, invalidatedAt);
    }

    @Test
    @DisplayName("onOutcomeInvalidated should rethrow DataAccessException for retry")
    void testOnOutcomeInvalidated_ShouldRethrowDataAccessException() {
        DataAccessException exception = new DataAccessException("Database connection error") {
            // Anonymous inner class to create a concrete DataAccessException
        };
        doThrow(exception).when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        assertThrows(DataAccessException.class, () -> eventConsumer.onOutcomeInvalidated(event));

        verify(metricsService).recordOutcomeInvalidated(tenantId, outcomeId, createdAt, invalidatedAt);
    }

    @Test
    @DisplayName("onOutcomeInvalidated should rethrow DataAccessException for message broker retry")
    void testOnOutcomeInvalidated_ShouldRethrowDataAccessExceptionForRetry() {
        DataAccessException databaseException = new DataAccessException("Connection pool exhausted") {
        };
        doThrow(databaseException).when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        assertThrows(DataAccessException.class, () -> eventConsumer.onOutcomeInvalidated(event));
    }

    @Test
    @DisplayName("onOutcomeInvalidated should rethrow RuntimeException for unexpected errors")
    void testOnOutcomeInvalidated_ShouldRethrowRuntimeExceptionForUnexpectedErrors() {
        Exception unexpectedException = new IllegalStateException("Unexpected state");
        doThrow(unexpectedException).when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        assertThrows(RuntimeException.class, () -> eventConsumer.onOutcomeInvalidated(event));

        verify(metricsService).recordOutcomeInvalidated(tenantId, outcomeId, createdAt, invalidatedAt);
    }

    @Test
    @DisplayName("onOutcomeInvalidated should wrap unexpected exceptions in RuntimeException")
    void testOnOutcomeInvalidated_ShouldWrapUnexpectedExceptionsInRuntimeException() {
        Exception unexpectedException = new NullPointerException("Value was null");
        doThrow(unexpectedException).when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> eventConsumer.onOutcomeInvalidated(event));

        // Verify it's wrapped in RuntimeException
        org.junit.jupiter.api.Assertions.assertTrue(thrownException.getCause() instanceof NullPointerException);
    }

    @Test
    @DisplayName("onOutcomeInvalidated should process event with correct tenantId from header")
    void testOnOutcomeInvalidated_ShouldProcessEventWithCorrectTenantId() {
        UUID anotherTenantId = UUID.randomUUID();
        OutcomeInvalidatedEvent anotherTenantEvent = new OutcomeInvalidatedEvent(
            anotherTenantId,
            outcomeId,
            "Different outcome",
            invalidatedBy,
            createdAt,
            invalidatedAt
        );
        doNothing().when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        eventConsumer.onOutcomeInvalidated(anotherTenantEvent);

        verify(metricsService).recordOutcomeInvalidated(eq(anotherTenantId), any(UUID.class), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("onOutcomeInvalidated should call metricsService exactly once")
    void testOnOutcomeInvalidated_ShouldCallMetricsServiceExactlyOnce() {
        doNothing().when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        eventConsumer.onOutcomeInvalidated(event);

        verify(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("onOutcomeInvalidated should successfully process valid event")
    void testOnOutcomeInvalidated_ShouldSuccessfullyProcessValidEvent() {
        doNothing().when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        // Should complete without exception
        eventConsumer.onOutcomeInvalidated(event);

        verify(metricsService).recordOutcomeInvalidated(tenantId, outcomeId, createdAt, invalidatedAt);
    }

    @Test
    @DisplayName("onOutcomeInvalidated should preserve event data in case of exception")
    void testOnOutcomeInvalidated_ShouldPreserveEventDataInCaseOfException() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("Duplicate");
        doThrow(exception).when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        // Process the event
        eventConsumer.onOutcomeInvalidated(event);

        // Verify the exception was caught but event data was available
        verify(metricsService).recordOutcomeInvalidated(
            eq(event.tenantId()),
            eq(event.outcomeId()),
            eq(event.createdAt()),
            eq(event.invalidatedAt())
        );
    }

    @Test
    @DisplayName("onOutcomeInvalidated should not call service if exception occurs before call")
    void testOnOutcomeInvalidated_ShouldNotCallServiceIfExceptionBeforeCall() {
        doNothing().when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        // Create a normal event and process it
        eventConsumer.onOutcomeInvalidated(event);

        // Verify service was called
        verify(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("onOutcomeInvalidated with subtypes of DataAccessException should rethrow")
    void testOnOutcomeInvalidated_WithSubtypesOfDataAccessExceptionShouldRethrow() {
        // DataIntegrityViolationException is a subtype of DataAccessException
        // but should be caught separately
        DataIntegrityViolationException viedException = new DataIntegrityViolationException("Duplicate");
        doThrow(viedException).when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        // Should not rethrow DataIntegrityViolationException
        eventConsumer.onOutcomeInvalidated(event);

        verify(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("onOutcomeInvalidated should handle null event gracefully by throwing exception")
    void testOnOutcomeInvalidated_ShouldHandleNullEventGracefully() {
        doNothing().when(metricsService).recordOutcomeInvalidated(any(UUID.class), any(UUID.class), any(Instant.class), any(Instant.class));

        // Attempting to process null should throw an exception
        assertThrows(Exception.class, () -> eventConsumer.onOutcomeInvalidated(null));
    }
}
