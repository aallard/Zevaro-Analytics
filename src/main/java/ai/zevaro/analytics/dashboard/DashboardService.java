package ai.zevaro.analytics.dashboard;

import ai.zevaro.analytics.client.CoreServiceClient;
import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.dashboard.dto.*;
import ai.zevaro.analytics.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final MetricSnapshotRepository snapshotRepository;
    private final DecisionCycleLogRepository cycleLogRepository;
    private final CoreServiceClient coreServiceClient;
    private final AnalyticsEventRepository analyticsEventRepository;

    @Cacheable(value = AppConstants.CACHE_DASHBOARD, key = "#tenantId + ':' + #projectId")
    public DashboardData getDashboard(UUID tenantId, @Nullable UUID projectId) {
        var now = Instant.now();
        var thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        // Get velocity trends
        var decisionTrend = getDecisionVelocityTrend(tenantId, projectId, 30);
        var outcomeTrend = getOutcomeVelocityTrend(tenantId, projectId, 30);

        // Calculate averages
        var avgCycleTime = projectId != null
            ? cycleLogRepository.findAvgCycleTimeSinceByProject(tenantId, projectId, thirtyDaysAgo)
            : cycleLogRepository.findAvgCycleTimeSince(tenantId, thirtyDaysAgo);
        var escalatedCount = projectId != null
            ? cycleLogRepository.countEscalatedSinceByProject(tenantId, projectId, thirtyDaysAgo)
            : cycleLogRepository.countEscalatedSince(tenantId, thirtyDaysAgo);

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

        // v2: Workstream counts from analytics events
        var workstreamCreatedEvents = safeList(analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_WORKSTREAM_CREATED, Instant.EPOCH));
        int totalWorkstreams = workstreamCreatedEvents.size();

        var workstreamsByMode = new HashMap<String, Integer>();
        var workstreamsByExecutionMode = new HashMap<String, Integer>();
        for (var ws : workstreamCreatedEvents) {
            var mode = getStringMeta(ws, "mode");
            if (mode != null) workstreamsByMode.merge(mode, 1, Integer::sum);
            var execMode = getStringMeta(ws, "executionMode");
            if (execMode != null) workstreamsByExecutionMode.merge(execMode, 1, Integer::sum);
        }

        var workstreamStatusEvents = safeList(analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_WORKSTREAM_STATUS_CHANGED, Instant.EPOCH));
        var terminalWorkstreams = new HashSet<UUID>();
        var terminalStatuses = Set.of("COMPLETED", "ARCHIVED");
        for (var event : workstreamStatusEvents) {
            if (terminalStatuses.contains(getStringMeta(event, "newStatus"))) {
                terminalWorkstreams.add(event.getEntityId());
            } else {
                terminalWorkstreams.remove(event.getEntityId());
            }
        }
        int activeWorkstreams = totalWorkstreams - terminalWorkstreams.size();

        // v2: Specification counts
        var specCreatedEvents = safeList(analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_SPEC_CREATED, Instant.EPOCH));
        int totalSpecifications = specCreatedEvents.size();

        var specApprovedEvents = safeList(analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_SPEC_APPROVED, Instant.EPOCH));
        var approvedSpecIds = specApprovedEvents.stream()
            .map(AnalyticsEvent::getEntityId)
            .collect(Collectors.toSet());

        var specStatusEvents = safeList(analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_SPEC_STATUS_CHANGED, Instant.EPOCH));
        var rejectedSpecIds = specStatusEvents.stream()
            .filter(e -> "REJECTED".equals(getStringMeta(e, "newStatus")))
            .map(AnalyticsEvent::getEntityId)
            .collect(Collectors.toSet());

        int specificationsPendingReview = (int) specCreatedEvents.stream()
            .map(AnalyticsEvent::getEntityId)
            .filter(id -> !approvedSpecIds.contains(id) && !rejectedSpecIds.contains(id))
            .count();

        var oneWeekAgo = now.minus(7, ChronoUnit.DAYS);
        int specificationsApprovedThisWeek = (int) specApprovedEvents.stream()
            .filter(e -> e.getEventTimestamp().isAfter(oneWeekAgo))
            .count();

        // v2: Ticket counts
        var ticketCreatedEvents = safeList(analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_TICKET_CREATED, Instant.EPOCH));
        int totalTickets = ticketCreatedEvents.size();

        var ticketResolvedEvents = safeList(analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_TICKET_RESOLVED, Instant.EPOCH));
        var resolvedTicketIds = ticketResolvedEvents.stream()
            .map(AnalyticsEvent::getEntityId)
            .collect(Collectors.toSet());

        int openTickets = (int) ticketCreatedEvents.stream()
            .map(AnalyticsEvent::getEntityId)
            .filter(id -> !resolvedTicketIds.contains(id))
            .count();

        var ticketsByStatus = Map.of("OPEN", openTickets, "RESOLVED", resolvedTicketIds.size());

        var ticketsBySeverity = new HashMap<String, Integer>();
        for (var t : ticketCreatedEvents) {
            var severity = getStringMeta(t, "severity");
            if (severity != null) ticketsBySeverity.merge(severity, 1, Integer::sum);
        }

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
            0,       // idleTimeMinutes - requires Elaro integration (ZI-009)
            totalWorkstreams,
            activeWorkstreams,
            workstreamsByMode,
            workstreamsByExecutionMode,
            totalSpecifications,
            specificationsPendingReview,
            specificationsApprovedThisWeek,
            totalTickets,
            openTickets,
            ticketsByStatus,
            ticketsBySeverity,
            0,   // totalDocuments - requires document consumer (ZI-TBD)
            0    // publishedDocuments - requires document consumer (ZI-TBD)
        );
    }

    @Cacheable(value = AppConstants.CACHE_DASHBOARD, key = "'summary:' + #tenantId + ':' + #projectId")
    public Map<String, Object> getDashboardSummary(UUID tenantId, @Nullable UUID projectId) {
        var thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        var avgCycleTime = projectId != null
            ? cycleLogRepository.findAvgCycleTimeSinceByProject(tenantId, projectId, thirtyDaysAgo)
            : cycleLogRepository.findAvgCycleTimeSince(tenantId, thirtyDaysAgo);

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

    private List<DataPoint> getDecisionVelocityTrend(UUID tenantId, @Nullable UUID projectId, int days) {
        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = projectId != null
            ? snapshotRepository.findByTenantIdAndProjectIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, projectId, AppConstants.METRIC_DECISION_VELOCITY, startDate, endDate)
            : snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_DECISION_VELOCITY, startDate, endDate);

        return snapshots.stream()
            .map(s -> new DataPoint(s.getMetricDate(), s.getValue().doubleValue()))
            .toList();
    }

    private List<DataPoint> getOutcomeVelocityTrend(UUID tenantId, @Nullable UUID projectId, int days) {
        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = projectId != null
            ? snapshotRepository.findByTenantIdAndProjectIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, projectId, AppConstants.METRIC_OUTCOME_VELOCITY, startDate, endDate)
            : snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
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

    private String getStringMeta(AnalyticsEvent event, String key) {
        if (event.getMetadata() == null || !event.getMetadata().containsKey(key)) return null;
        return event.getMetadata().get(key).toString();
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : List.of();
    }
}
