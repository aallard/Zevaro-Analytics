package ai.zevaro.analytics.metrics.dto;

import java.util.Map;

public record TicketVelocityMetric(
    double avgResolutionHours,
    int totalResolved,
    int totalOpen,
    Map<String, Double> avgResolutionBySeverity
) {}
