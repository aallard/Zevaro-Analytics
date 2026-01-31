package ai.zevaro.analytics.metrics.dto;

import java.time.LocalDate;
import java.util.UUID;

public record OutcomeVelocityMetric(
    UUID tenantId,
    LocalDate date,
    int outcomesValidated,
    int outcomesInvalidated,
    int outcomesInProgress,
    double avgTimeToValidationDays
) {}
