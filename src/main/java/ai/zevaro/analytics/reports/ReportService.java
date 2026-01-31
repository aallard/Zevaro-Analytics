package ai.zevaro.analytics.reports;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.dashboard.dto.DataPoint;
import ai.zevaro.analytics.reports.dto.*;
import ai.zevaro.analytics.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final MetricSnapshotRepository snapshotRepository;
    private final DecisionCycleLogRepository cycleLogRepository;

    @Cacheable(value = AppConstants.CACHE_REPORTS, key = "'weekly:' + #tenantId + ':' + #weekStart")
    public WeeklyDigestReport generateWeeklyDigest(UUID tenantId, LocalDate weekStart) {
        var weekEnd = weekStart.plusDays(7);
        var prevWeekStart = weekStart.minusDays(7);

        var startInstant = weekStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        var endInstant = weekEnd.atStartOfDay().toInstant(ZoneOffset.UTC);
        var prevStartInstant = prevWeekStart.atStartOfDay().toInstant(ZoneOffset.UTC);

        // This week's data
        var thisWeekLogs = cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            tenantId, startInstant, endInstant);

        var prevWeekLogs = cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            tenantId, prevStartInstant, startInstant);

        var thisWeekAvg = thisWeekLogs.stream()
            .mapToDouble(l -> l.getCycleTimeHours().doubleValue())
            .average()
            .orElse(0.0);

        var prevWeekAvg = prevWeekLogs.stream()
            .mapToDouble(l -> l.getCycleTimeHours().doubleValue())
            .average()
            .orElse(0.0);

        var changePercent = prevWeekAvg > 0
            ? ((thisWeekAvg - prevWeekAvg) / prevWeekAvg) * 100
            : 0.0;

        // Daily velocity
        var dailyVelocity = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_DECISION_VELOCITY, weekStart, weekEnd)
            .stream()
            .map(s -> new DataPoint(s.getMetricDate(), s.getValue().doubleValue()))
            .toList();

        // Outcomes
        var outcomeSnapshots = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, weekStart, weekEnd);
        var outcomesValidated = outcomeSnapshots.stream()
            .mapToInt(s -> s.getValue().intValue())
            .sum();

        // Generate highlights
        var highlights = new ArrayList<String>();
        var concerns = new ArrayList<String>();

        if (changePercent < -10) {
            highlights.add(String.format("Decision velocity improved by %.1f%% vs last week", Math.abs(changePercent)));
        } else if (changePercent > 10) {
            concerns.add(String.format("Decision velocity slowed by %.1f%% vs last week", changePercent));
        }

        if (thisWeekLogs.size() > prevWeekLogs.size()) {
            highlights.add(String.format("Resolved %d more decisions than last week",
                thisWeekLogs.size() - prevWeekLogs.size()));
        }

        return new WeeklyDigestReport(
            tenantId,
            weekStart,
            weekEnd,
            thisWeekLogs.size(),
            0,  // decisionsCreated - would need tracking
            thisWeekAvg,
            changePercent,
            outcomesValidated,
            0,  // outcomesInvalidated
            0,  // hypothesesTested
            dailyVelocity,
            List.of(),  // topStakeholders
            highlights,
            concerns
        );
    }

    public OutcomeReport generateOutcomeReport(UUID tenantId, UUID outcomeId) {
        // This would need integration with Core service to get outcome details
        // For now, return a placeholder structure
        return new OutcomeReport(
            outcomeId,
            "Outcome Report",
            "IN_PROGRESS",
            Instant.now().minus(30, ChronoUnit.DAYS),
            null,
            0, 0, 0.0,
            0, 0, 0, 0,
            List.of(),
            List.of()
        );
    }

    public List<Map<String, Object>> listAvailableReports(UUID tenantId) {
        return List.of(
            Map.of(
                "type", "WEEKLY_DIGEST",
                "name", "Weekly Digest",
                "description", "Summary of decision velocity and outcomes for the past week"
            ),
            Map.of(
                "type", "OUTCOME_REPORT",
                "name", "Outcome Report",
                "description", "Detailed report for a specific outcome"
            ),
            Map.of(
                "type", "STAKEHOLDER_PERFORMANCE",
                "name", "Stakeholder Performance",
                "description", "Response times and SLA compliance by stakeholder"
            )
        );
    }
}
