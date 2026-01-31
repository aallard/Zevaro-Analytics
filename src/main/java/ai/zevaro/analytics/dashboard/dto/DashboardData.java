package ai.zevaro.analytics.dashboard.dto;

import java.time.Instant;
import java.util.List;

public record DashboardData(
    // Summary cards
    int decisionsPendingCount,
    double avgDecisionWaitHours,
    int outcomesValidatedThisWeek,
    int hypothesesTestedThisWeek,
    int experimentsRunning,

    // Decision health
    String decisionHealthStatus,  // GREEN, YELLOW, RED
    List<DecisionSummary> urgentDecisions,

    // Charts data
    List<DataPoint> decisionVelocityTrend,
    List<DataPoint> outcomeVelocityTrend,
    List<StakeholderScore> stakeholderLeaderboard,

    // Pipeline status
    String pipelineStatus,
    Instant lastDeployment,
    long idleTimeMinutes
) {}
