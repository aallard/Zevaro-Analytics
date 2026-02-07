package ai.zevaro.analytics.reports;

import ai.zevaro.analytics.client.CoreServiceClient;
import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.dashboard.dto.DataPoint;
import ai.zevaro.analytics.reports.dto.*;
import ai.zevaro.analytics.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ReportRepository reportRepository;
    private final CoreServiceClient coreServiceClient;
    private final ObjectMapper objectMapper;

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

        // Outcomes validated + invalidated
        var outcomeSnapshots = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, weekStart, weekEnd);
        var outcomesValidated = outcomeSnapshots.stream()
            .mapToInt(s -> s.getValue().intValue())
            .sum();
        var outcomesInvalidated = outcomeSnapshots.stream()
            .mapToInt(s -> getIntDimension(s.getDimensions(), "invalidated"))
            .sum();

        // Hypotheses tested this week
        var hypothesisSnapshots = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_HYPOTHESIS_THROUGHPUT, weekStart, weekEnd);
        var hypothesesTested = hypothesisSnapshots.stream()
            .mapToInt(s -> s.getValue().intValue())
            .sum();

        // Decisions created from Core
        var decisionsCreated = coreServiceClient.getDecisionsCreatedCount(tenantId);

        // Top stakeholders by response time
        var stakeholderData = cycleLogRepository.findAvgCycleTimeByStakeholder(tenantId, startInstant);
        var topStakeholders = stakeholderData.stream()
            .limit(5)
            .map(row -> {
                var stakeholderId = (UUID) row[0];
                var avgTime = ((Number) row[1]).doubleValue();
                var info = coreServiceClient.getStakeholder(tenantId, stakeholderId);
                var name = info != null ? info.name() : stakeholderId.toString().substring(0, 8);
                return String.format("%s (%.1fh avg)", name, avgTime);
            })
            .toList();

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

        if (outcomesValidated > 0) {
            highlights.add(String.format("%d outcomes validated this week", outcomesValidated));
        }

        if (hypothesesTested > 0) {
            highlights.add(String.format("%d hypotheses tested this week", hypothesesTested));
        }

        var report = new WeeklyDigestReport(
            tenantId,
            weekStart,
            weekEnd,
            thisWeekLogs.size(),
            decisionsCreated,
            thisWeekAvg,
            changePercent,
            outcomesValidated,
            outcomesInvalidated,
            hypothesesTested,
            dailyVelocity,
            topStakeholders,
            highlights,
            concerns
        );

        // Persist report for historical access
        persistReport(tenantId, "WEEKLY_DIGEST", weekStart, weekEnd, report);

        return report;
    }

    public OutcomeReport generateOutcomeReport(UUID tenantId, UUID outcomeId) {
        // Fetch outcome details from Core service
        var outcomeInfo = coreServiceClient.getOutcome(tenantId, outcomeId);

        String title = "Outcome Report";
        String status = "UNKNOWN";
        Instant createdAt = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant validatedAt = null;

        if (outcomeInfo != null) {
            title = outcomeInfo.title();
            status = outcomeInfo.status();
            createdAt = outcomeInfo.createdAt();
            validatedAt = outcomeInfo.validatedAt();
        }

        // Get decisions resolved in this outcome's lifetime
        var logs = cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            tenantId, createdAt, Instant.now());

        var totalDecisions = logs.size();
        var avgDecisionTime = logs.stream()
            .mapToDouble(l -> l.getCycleTimeHours().doubleValue())
            .average()
            .orElse(0.0);

        // Hypotheses throughput for this outcome period
        var startDate = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
        var endDate = LocalDate.now();
        var hypothesisSnapshots = snapshotRepository
            .findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_HYPOTHESIS_THROUGHPUT, startDate, endDate);

        int totalHypotheses = hypothesisSnapshots.stream()
            .mapToInt(s -> s.getValue().intValue())
            .sum();
        int hypothesesValidated = hypothesisSnapshots.stream()
            .mapToInt(s -> getIntDimension(s.getDimensions(), "validated"))
            .sum();
        int hypothesesInvalidated = hypothesisSnapshots.stream()
            .mapToInt(s -> getIntDimension(s.getDimensions(), "invalidated"))
            .sum();
        int hypothesesInProgress = coreServiceClient.getActiveHypothesisCount(tenantId);

        // Build timeline from decision cycle logs
        var timeline = logs.stream()
            .sorted(Comparator.comparing(DecisionCycleLog::getResolvedAt).reversed())
            .limit(20)
            .map(l -> new TimelineEvent(
                l.getResolvedAt(),
                "DECISION_RESOLVED",
                String.format("Decision resolved in %.1fh (%s priority)",
                    l.getCycleTimeHours().doubleValue(), l.getPriority())
            ))
            .toList();

        var report = new OutcomeReport(
            outcomeId,
            title,
            status,
            createdAt,
            validatedAt,
            totalDecisions,
            totalDecisions,
            avgDecisionTime,
            totalHypotheses,
            hypothesesValidated,
            hypothesesInvalidated,
            hypothesesInProgress,
            List.of(),  // keyResults - requires Core service outcome.metrics integration
            timeline
        );

        // Persist report
        persistReport(tenantId, "OUTCOME_REPORT", startDate, endDate, report);

        return report;
    }

    public List<Map<String, Object>> listAvailableReports(UUID tenantId) {
        var persisted = reportRepository.findByTenantIdOrderByGeneratedAtDesc(tenantId);
        var persistedList = persisted.stream()
            .limit(10)
            .map(r -> Map.<String, Object>of(
                "id", r.getId(),
                "type", r.getReportType(),
                "periodStart", r.getPeriodStart(),
                "periodEnd", r.getPeriodEnd(),
                "generatedAt", r.getGeneratedAt()
            ))
            .toList();

        var reportTypes = List.of(
            Map.<String, Object>of(
                "type", "WEEKLY_DIGEST",
                "name", "Weekly Digest",
                "description", "Summary of decision velocity and outcomes for the past week"
            ),
            Map.<String, Object>of(
                "type", "OUTCOME_REPORT",
                "name", "Outcome Report",
                "description", "Detailed report for a specific outcome"
            ),
            Map.<String, Object>of(
                "type", "STAKEHOLDER_PERFORMANCE",
                "name", "Stakeholder Performance",
                "description", "Response times and SLA compliance by stakeholder"
            )
        );

        var result = new ArrayList<Map<String, Object>>();
        result.add(Map.of("reportTypes", reportTypes));
        result.add(Map.of("recentReports", persistedList));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void persistReport(UUID tenantId, String reportType, LocalDate periodStart, LocalDate periodEnd, Object reportData) {
        try {
            var dataMap = objectMapper.convertValue(reportData, Map.class);

            var existing = reportRepository.findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
                tenantId, reportType, periodStart, periodEnd);

            if (existing.isPresent()) {
                var report = existing.get();
                report.setData(dataMap);
                report.setGeneratedAt(Instant.now());
                reportRepository.save(report);
            } else {
                var report = Report.builder()
                    .tenantId(tenantId)
                    .reportType(reportType)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .data(dataMap)
                    .generatedAt(Instant.now())
                    .build();
                reportRepository.save(report);
            }
        } catch (Exception e) {
            log.warn("Failed to persist report: {}", e.getMessage());
        }
    }

    private int getIntDimension(Map<String, Object> dimensions, String key) {
        if (dimensions == null || !dimensions.containsKey(key)) return 0;
        return ((Number) dimensions.get(key)).intValue();
    }
}
