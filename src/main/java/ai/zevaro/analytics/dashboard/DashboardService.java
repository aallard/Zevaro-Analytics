package ai.zevaro.analytics.dashboard;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.dashboard.dto.*;
import ai.zevaro.analytics.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final MetricSnapshotRepository snapshotRepository;
    private final DecisionCycleLogRepository cycleLogRepository;

    @Cacheable(value = AppConstants.CACHE_DASHBOARD, key = "#tenantId")
    public DashboardData getDashboard(UUID tenantId) {
        var now = Instant.now();
        var thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        // Get velocity trends
        var decisionTrend = getDecisionVelocityTrend(tenantId, 30);
        var outcomeTrend = getOutcomeVelocityTrend(tenantId, 30);

        // Calculate averages
        var avgCycleTime = cycleLogRepository.findAvgCycleTimeSince(tenantId, thirtyDaysAgo);
        var escalatedCount = cycleLogRepository.countEscalatedSince(tenantId, thirtyDaysAgo);

        // Outcomes this week
        var weekStart = LocalDate.now().minusDays(7);
        var weekEnd = LocalDate.now();
        var weekOutcomes = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, weekStart, weekEnd);
        var outcomesThisWeek = weekOutcomes.stream()
            .mapToInt(s -> s.getValue().intValue())
            .sum();

        // Stakeholder leaderboard
        var stakeholderData = cycleLogRepository.findAvgCycleTimeByStakeholder(tenantId, thirtyDaysAgo);
        var leaderboard = buildLeaderboard(stakeholderData);

        // Decision health
        var healthStatus = calculateHealthStatus(avgCycleTime, escalatedCount);

        return new DashboardData(
            0,  // decisionsPendingCount - would need live data from Core
            avgCycleTime != null ? avgCycleTime : 0.0,
            outcomesThisWeek,
            0,  // hypothesesTestedThisWeek - would need tracking
            0,  // experimentsRunning - would need live data
            healthStatus,
            List.of(),  // urgentDecisions - would need live data from Core
            decisionTrend,
            outcomeTrend,
            leaderboard,
            "IDLE",  // pipelineStatus - would need integration
            null,    // lastDeployment
            0        // idleTimeMinutes
        );
    }

    @Cacheable(value = AppConstants.CACHE_DASHBOARD, key = "'summary:' + #tenantId")
    public Map<String, Object> getDashboardSummary(UUID tenantId) {
        var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        var avgCycleTime = cycleLogRepository.findAvgCycleTimeSince(tenantId, thirtyDaysAgo);

        return Map.of(
            "avgDecisionTimeHours", avgCycleTime != null ? avgCycleTime : 0.0,
            "healthStatus", calculateHealthStatus(avgCycleTime, 0L),
            "lastUpdated", Instant.now()
        );
    }

    private List<DataPoint> getDecisionVelocityTrend(UUID tenantId, int days) {
        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_DECISION_VELOCITY, startDate, endDate);

        return snapshots.stream()
            .map(s -> new DataPoint(s.getMetricDate(), s.getValue().doubleValue()))
            .toList();
    }

    private List<DataPoint> getOutcomeVelocityTrend(UUID tenantId, int days) {
        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, startDate, endDate);

        return snapshots.stream()
            .map(s -> new DataPoint(s.getMetricDate(), s.getValue().doubleValue()))
            .toList();
    }

    private List<StakeholderScore> buildLeaderboard(List<Object[]> data) {
        var scores = new ArrayList<StakeholderScore>();
        int rank = 1;

        for (var row : data) {
            scores.add(new StakeholderScore(
                (UUID) row[0],
                null,  // Name would come from Core service
                ((Number) row[1]).doubleValue(),
                0,     // decisionsCompleted
                0.0,   // slaComplianceRate
                rank++
            ));
        }

        return scores;
    }

    private String calculateHealthStatus(Double avgCycleTime, Long escalatedCount) {
        if (avgCycleTime == null) return "GREEN";

        // GREEN: avg < 24h, RED: avg > 72h, YELLOW: in between
        if (avgCycleTime < 24) return "GREEN";
        if (avgCycleTime > 72) return "RED";
        return "YELLOW";
    }
}
