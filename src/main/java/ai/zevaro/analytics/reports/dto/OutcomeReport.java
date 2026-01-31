package ai.zevaro.analytics.reports.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OutcomeReport(
    UUID outcomeId,
    String outcomeTitle,
    String status,
    Instant createdAt,
    Instant validatedAt,

    // Decisions
    int totalDecisions,
    int decisionsResolved,
    double avgDecisionTimeHours,

    // Hypotheses
    int totalHypotheses,
    int hypothesesValidated,
    int hypothesesInvalidated,
    int hypothesesInProgress,

    // Key results
    List<KeyResultProgress> keyResults,

    // Timeline
    List<TimelineEvent> timeline
) {}
