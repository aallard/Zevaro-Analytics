package ai.zevaro.analytics.consumer.events;

import java.time.Instant;
import java.util.UUID;

public record DecisionResolvedEvent(
    UUID tenantId,
    UUID decisionId,
    String title,
    String priority,
    String decisionType,
    UUID resolvedBy,
    UUID stakeholderId,
    boolean wasEscalated,
    Instant createdAt,
    Instant resolvedAt
) {}
