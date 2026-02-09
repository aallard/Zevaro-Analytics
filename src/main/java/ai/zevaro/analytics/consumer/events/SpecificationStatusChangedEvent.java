package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record SpecificationStatusChangedEvent(
    UUID tenantId,
    UUID specificationId,
    String oldStatus,
    String newStatus,
    UUID changedById,
    Instant timestamp
) {}
