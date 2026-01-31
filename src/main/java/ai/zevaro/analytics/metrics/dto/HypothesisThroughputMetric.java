package ai.zevaro.analytics.metrics.dto;

import java.time.LocalDate;
import java.util.UUID;

public record HypothesisThroughputMetric(
    UUID tenantId,
    LocalDate date,
    int hypothesesTested,
    int hypothesesValidated,
    int hypothesesInvalidated,
    double validationRate
) {}
