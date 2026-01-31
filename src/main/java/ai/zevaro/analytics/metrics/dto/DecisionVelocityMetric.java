package ai.zevaro.analytics.metrics.dto;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record DecisionVelocityMetric(
    UUID tenantId,
    LocalDate date,
    double avgCycleTimeHours,
    int decisionsCreated,
    int decisionsResolved,
    int decisionsEscalated,
    double escalationRate,
    Map<String, Double> byPriority,
    Map<String, Double> byType
) {}
