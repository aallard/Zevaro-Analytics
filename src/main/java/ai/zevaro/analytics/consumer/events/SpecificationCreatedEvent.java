package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record SpecificationCreatedEvent(
    UUID tenantId,
    UUID specificationId,
    UUID workstreamId,
    UUID programId,
    String name,
    UUID authorId,
    Instant timestamp
) {}
