package ai.zevaro.analytics.metrics.dto;

import java.util.List;

public record SpecificationVelocityMetric(
    double avgApprovalCycleHours,
    int totalApproved,
    int totalRejected,
    int approvedThisWeek,
    List<WeeklyCount> weeklyTrend
) {}
