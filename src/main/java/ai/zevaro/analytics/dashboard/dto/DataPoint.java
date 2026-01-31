package ai.zevaro.analytics.dashboard.dto;

import java.time.LocalDate;

public record DataPoint(
    LocalDate date,
    double value
) {}
