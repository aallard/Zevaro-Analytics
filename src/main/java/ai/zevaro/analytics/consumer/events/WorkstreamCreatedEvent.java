package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record WorkstreamCreatedEvent(
    UUID tenantId,
    UUID workstreamId,
    UUID programId,
    String name,
    String mode,
    String executionMode,
    UUID createdById,
    Instant timestamp
) {}
