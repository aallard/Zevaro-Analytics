package ai.zevaro.analytics.dashboard;

import ai.zevaro.analytics.client.CoreServiceClient;
import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.dashboard.dto.*;
import ai.zevaro.analytics.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private final CoreServiceClient coreServiceClient;

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

        // Stakeholder leaderboard — enriched with Core data
        var stakeholderData = cycleLogRepository.findAvgCycleTimeByStakeholder(tenantId, thirtyDaysAgo);
        var leaderboard = buildLeaderboard(tenantId, stakeholderData);

        // Decision health
        var healthStatus = calculateHealthStatus(avgCycleTime, escalatedCount);

        // Live data from Core service
        var pendingDecisionCount = coreServiceClient.getPendingDecisionCount(tenantId);
        var hypothesesTestedThisWeek = coreServiceClient.getHypothesesTestedThisWeek(tenantId);
        var activeExperiments = coreServiceClient.getActiveHypothesisCount(tenantId);
        var urgentDecisions = buildUrgentDecisionSummaries(tenantId);

        return new DashboardData(
            pendingDecisionCount,
            avgCycleTime != null ? avgCycleTime : 0.0,
            outcomesThisWeek,
            hypothesesTestedThisWeek,
            activeExperiments,
            healthStatus,
            urgentDecisions,
            decisionTrend,
            outcomeTrend,
            leaderboard,
            "IDLE",  // pipelineStatus - requires Elaro integration (ZI-009)
            null,    // lastDeployment - requires Elaro integration (ZI-009)
            0        // idleTimeMinutes - requires Elaro integration (ZI-009)
        );
    }

    @Cacheable(value = AppConstants.CACHE_DASHBOARD, key = "'summary:' + #tenantId")
    public Map<String, Object> getDashboardSummary(UUID tenantId) {
        var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        var avgCycleTime = cycleLogRepository.findAvgCycleTimeSince(tenantId, thirtyDaysAgo);

        return Map.of(
            "avgDecisionTimeHours", avgCycleTime != null ? avgCycleTime : 0.0,
            "healthStatus", calculateHealthStatus(avgCycleTime, 0L),
            "pendingDecisions", coreServiceClient.getPendingDecisionCount(tenantId),
            "lastUpdated", Instant.now()
        );
    }

    private List<DecisionSummary> buildUrgentDecisionSummaries(UUID tenantId) {
        var urgent = coreServiceClient.getUrgentDecisions(tenantId);
        return urgent.stream()
            .map(d -> new DecisionSummary(
                d.id(),
                d.title(),
                d.priority(),
                d.ownerName(),
                Duration.between(d.createdAt(), Instant.now()).toHours(),
                (int) d.blockedItemsCount()
            ))
            .toList();
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

    private List<StakeholderScore> buildLeaderboard(UUID tenantId, List<Object[]> data) {
        var scores = new ArrayList<StakeholderScore>();
        int rank = 1;

        for (var row : data) {
            var stakeholderId = (UUID) row[0];
            var avgTime = ((Number) row[1]).doubleValue();

            // Enrich with stakeholder name and stats from Core
            String name = null;
            int decisionsCompleted = 0;
            double slaComplianceRate = 0.0;

            var stakeholderInfo = coreServiceClient.getStakeholder(tenantId, stakeholderId);
            if (stakeholderInfo != null) {
                name = stakeholderInfo.name();
                decisionsCompleted = stakeholderInfo.decisionsCompleted();
                slaComplianceRate = decisionsCompleted > 0 && avgTime < 24.0 ? 1.0
                    : decisionsCompleted > 0 && avgTime < 48.0 ? 0.75
                    : decisionsCompleted > 0 ? 0.5
                    : 0.0;
            }

            scores.add(new StakeholderScore(
                stakeholderId,
                name,
                avgTime,
                decisionsCompleted,
                slaComplianceRate,
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
