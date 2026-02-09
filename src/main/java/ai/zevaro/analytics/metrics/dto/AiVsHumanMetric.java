package ai.zevaro.analytics.metrics.dto;

public record AiVsHumanMetric(
    double aiFirstAvgResolutionHours,
    double traditionalAvgResolutionHours,
    double hybridAvgResolutionHours,
    int aiFirstCount,
    int traditionalCount,
    int hybridCount,
    double speedupFactor
) {}
