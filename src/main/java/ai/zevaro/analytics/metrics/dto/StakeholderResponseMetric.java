package ai.zevaro.analytics.metrics.dto;

import java.time.LocalDate;
import java.util.UUID;

public record StakeholderResponseMetric(
    UUID stakeholderId,
    String stakeholderName,
    double avgResponseTimeHours,
    int decisionsPending,
    int decisionsCompleted,
    double escalationRate,
    LocalDate periodStart,
    LocalDate periodEnd
) {}
