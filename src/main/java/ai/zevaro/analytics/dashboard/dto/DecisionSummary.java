package ai.zevaro.analytics.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

public record DecisionSummary(
    UUID id,
    String title,
    String urgency,
    String status,
    Instant createdAt,
    Instant slaDeadline,
    boolean isOverdue
) {}
