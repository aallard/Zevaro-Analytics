package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record WorkstreamStatusChangedEvent(
    UUID tenantId,
    UUID workstreamId,
    String oldStatus,
    String newStatus,
    UUID changedById,
    Instant timestamp
) {}
