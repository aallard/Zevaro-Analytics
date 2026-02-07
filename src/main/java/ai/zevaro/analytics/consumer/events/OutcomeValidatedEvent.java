package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record OutcomeValidatedEvent(
    UUID tenantId,
    UUID projectId,
    UUID outcomeId,
    String title,
    UUID validatedBy,
    Instant createdAt,
    Instant validatedAt
) {}
