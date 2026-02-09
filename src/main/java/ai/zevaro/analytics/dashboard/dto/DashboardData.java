package ai.zevaro.analytics.dashboard.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    long idleTimeMinutes,

    // v2: Workstreams
    int totalWorkstreams,
    int activeWorkstreams,
    Map<String, Integer> workstreamsByMode,
    Map<String, Integer> workstreamsByExecutionMode,

    // v2: Specifications
    int totalSpecifications,
    int specificationsPendingReview,
    int specificationsApprovedThisWeek,

    // v2: Tickets
    int totalTickets,
    int openTickets,
    Map<String, Integer> ticketsByStatus,
    Map<String, Integer> ticketsBySeverity,

    // v2: Documents
    int totalDocuments,
    int publishedDocuments
) {}
