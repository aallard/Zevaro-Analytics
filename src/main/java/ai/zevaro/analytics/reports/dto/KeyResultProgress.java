package ai.zevaro.analytics.reports.dto;

public record KeyResultProgress(
    String title,
    double targetValue,
    double currentValue,
    String unit,
    double progressPercent
) {}
