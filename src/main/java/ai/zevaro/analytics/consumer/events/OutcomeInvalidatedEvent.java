package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record OutcomeInvalidatedEvent(
    UUID tenantId,
    UUID projectId,
    UUID outcomeId,
    String title,
    UUID invalidatedBy,
    Instant createdAt,
    Instant invalidatedAt
) {}
