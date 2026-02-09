package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record SpecificationApprovedEvent(
    UUID tenantId,
    UUID specificationId,
    UUID approvedById,
    Instant timestamp
) {}
