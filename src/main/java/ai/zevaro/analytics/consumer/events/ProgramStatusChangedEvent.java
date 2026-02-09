package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record ProgramStatusChangedEvent(
    UUID tenantId,
    UUID programId,
    String oldStatus,
    String newStatus,
    UUID changedById,
    Instant timestamp
) {}
