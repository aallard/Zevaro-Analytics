package ai.zevaro.analytics.dashboard.dto;

import java.util.UUID;

public record DecisionSummary(
    UUID id,
    String title,
    String priority,
    String ownerName,
    long waitingHours,
    int blockedItemsCount
) {}
