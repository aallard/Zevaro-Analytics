package ai.zevaro.analytics.dashboard.dto;

import java.util.UUID;

public record StakeholderScore(
    UUID stakeholderId,
    String name,
    double avgResponseTimeHours,
    int decisionsCompleted,
    double slaComplianceRate,
    int rank
) {}
