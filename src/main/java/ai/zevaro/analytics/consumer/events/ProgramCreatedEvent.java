package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record ProgramCreatedEvent(
    UUID tenantId,
    UUID programId,
    String name,
    String status,
    UUID createdById,
    Instant timestamp
) {}
