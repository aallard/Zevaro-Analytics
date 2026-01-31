package ai.zevaro.analytics.reports.dto;

import ai.zevaro.analytics.dashboard.dto.DataPoint;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WeeklyDigestReport(
    UUID tenantId,
    LocalDate weekStart,
    LocalDate weekEnd,

    // Summary
    int decisionsResolved,
    int decisionsCreated,
    double avgCycleTimeHours,
    double cycleTimeChangePercent,  // vs previous week

    int outcomesValidated,
    int outcomesInvalidated,
    int hypothesesTested,

    // Trends
    List<DataPoint> dailyDecisionVelocity,

    // Top performers
    List<String> topStakeholders,

    // Highlights
    List<String> highlights,
    List<String> concerns
) {}
