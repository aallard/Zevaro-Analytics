package ai.zevaro.analytics.metrics;

import ai.zevaro.analytics.client.CoreServiceClient;
import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.metrics.dto.*;
import ai.zevaro.analytics.repository.AnalyticsEvent;
import ai.zevaro.analytics.repository.AnalyticsEventRepository;
import ai.zevaro.analytics.repository.DecisionCycleLogRepository;
import ai.zevaro.analytics.repository.MetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping(AppConstants.API_V1 + "/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricSnapshotRepository snapshotRepository;
    private final DecisionCycleLogRepository cycleLogRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final CoreServiceClient coreServiceClient;

    @GetMapping("/decision-velocity")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'dv:' + #tenantId + ':' + #projectId + ':' + #days")
    public ResponseEntity<List<DecisionVelocityMetric>> getDecisionVelocity(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {

        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = projectId != null
            ? snapshotRepository.findByTenantIdAndProjectIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, projectId, AppConstants.METRIC_DECISION_VELOCITY, startDate, endDate)
            : snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_DECISION_VELOCITY, startDate, endDate);

        var metrics = snapshots.stream()
            .map(s -> new DecisionVelocityMetric(
                tenantId,
                s.getMetricDate(),
                s.getValue().doubleValue(),
                getIntDimension(s.getDimensions(), "decisionsCreated"),
                getIntDimension(s.getDimensions(), "decisionsResolved"),
                getIntDimension(s.getDimensions(), "escalatedCount"),
                getDoubleDimension(s.getDimensions(), "escalationRate"),
                Map.of(),  // byPriority - expandable per-priority breakdown
                Map.of()   // byType - expandable per-type breakdown
            ))
            .toList();

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/stakeholder-response")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'sr:' + #tenantId + ':' + #days")
    public ResponseEntity<List<StakeholderResponseMetric>> getStakeholderResponse(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "30") int days) {

        var since = Instant.now().minus(java.time.Duration.ofDays(days));
        var results = cycleLogRepository.findAvgCycleTimeByStakeholder(tenantId, since);

        var metrics = results.stream()
            .map(row -> {
                var stakeholderId = (UUID) row[0];
                var avgResponseTime = ((Number) row[1]).doubleValue();

                // Enrich with stakeholder data from Core
                String name = null;
                int pending = 0;
                int completed = 0;

                var info = coreServiceClient.getStakeholder(tenantId, stakeholderId);
                if (info != null) {
                    name = info.name();
                    pending = info.decisionsPending();
                    completed = info.decisionsCompleted();
                }

                var escalationRate = completed > 0 && avgResponseTime > 48.0 ? 0.3
                    : completed > 0 && avgResponseTime > 24.0 ? 0.15
                    : 0.05;

                return new StakeholderResponseMetric(
                    stakeholderId,
                    name,
                    avgResponseTime,
                    pending,
                    completed,
                    escalationRate,
                    LocalDate.now().minusDays(days),
                    LocalDate.now()
                );
            })
            .toList();

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/outcome-velocity")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'ov:' + #tenantId + ':' + #projectId + ':' + #days")
    public ResponseEntity<Map<String, Object>> getOutcomeVelocity(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {

        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = projectId != null
            ? snapshotRepository.findByTenantIdAndProjectIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, projectId, AppConstants.METRIC_OUTCOME_VELOCITY, startDate, endDate)
            : snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, startDate, endDate);

        var totalValidated = snapshots.stream()
            .mapToInt(s -> s.getValue().intValue())
            .sum();

        var totalInvalidated = snapshots.stream()
            .mapToInt(s -> getIntDimension(s.getDimensions(), "invalidated"))
            .sum();

        var dataPoints = snapshots.stream()
            .map(s -> Map.of(
                "date", s.getMetricDate().toString(),
                "validated", s.getValue().intValue(),
                "invalidated", getIntDimension(s.getDimensions(), "invalidated")
            ))
            .toList();

        return ResponseEntity.ok(Map.of(
            "totalValidated", totalValidated,
            "totalInvalidated", totalInvalidated,
            "periodDays", days,
            "dataPoints", dataPoints
        ));
    }

    @GetMapping("/hypothesis-throughput")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'ht:' + #tenantId + ':' + #projectId + ':' + #days")
    public ResponseEntity<List<HypothesisThroughputMetric>> getHypothesisThroughput(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {

        var endDate = LocalDate.now();
        var startDate = endDate.minusDays(days);

        var snapshots = projectId != null
            ? snapshotRepository.findByTenantIdAndProjectIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, projectId, AppConstants.METRIC_HYPOTHESIS_THROUGHPUT, startDate, endDate)
            : snapshotRepository.findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
                tenantId, AppConstants.METRIC_HYPOTHESIS_THROUGHPUT, startDate, endDate);

        var metrics = snapshots.stream()
            .map(s -> {
                int validated = getIntDimension(s.getDimensions(), "validated");
                int invalidated = getIntDimension(s.getDimensions(), "invalidated");
                int total = validated + invalidated;
                double rate = total > 0 ? (double) validated / total : 0.0;

                return new HypothesisThroughputMetric(
                    tenantId,
                    s.getMetricDate(),
                    total,
                    validated,
                    invalidated,
                    rate
                );
            })
            .toList();

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/pipeline-idle-time")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'pit:' + #tenantId")
    public ResponseEntity<Map<String, Object>> getPipelineIdleTime(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {

        var pendingDecisions = coreServiceClient.getPendingDecisionCount(tenantId);

        return ResponseEntity.ok(Map.of(
            "idleTimeMinutes", 0,  // Requires Elaro integration (ZI-009)
            "lastDecisionResolved", Instant.now(),
            "pendingDecisions", pendingDecisions,
            "status", pendingDecisions > 0 ? "WAITING_FOR_DECISIONS" : "READY"
        ));
    }

    // ── New v2 metric endpoints ────────────────────────────────────────

    @GetMapping("/specification-velocity")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'sv:' + #tenantId + ':' + #programId + ':' + #days")
    public ResponseEntity<SpecificationVelocityMetric> getSpecificationVelocity(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID programId,
            @RequestParam(defaultValue = "30") int days) {

        var since = Instant.now().minus(Duration.ofDays(days));

        // Fetch approved events within the period
        var approvedEvents = programId != null
            ? analyticsEventRepository.findByTenantIdAndEventTypeAndParentIdAndEventTimestampAfter(
                tenantId, AppConstants.EVENT_SPEC_APPROVED, programId, since)
            : analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
                tenantId, AppConstants.EVENT_SPEC_APPROVED, since);

        // Fetch rejected (status changed to REJECTED) within the period
        var statusEvents = programId != null
            ? analyticsEventRepository.findByTenantIdAndEventTypeAndParentIdAndEventTimestampAfter(
                tenantId, AppConstants.EVENT_SPEC_STATUS_CHANGED, programId, since)
            : analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
                tenantId, AppConstants.EVENT_SPEC_STATUS_CHANGED, since);

        int totalRejected = (int) statusEvents.stream()
            .filter(e -> "REJECTED".equals(getStringMeta(e, "newStatus")))
            .count();

        // Cross-reference with creation events for cycle time
        var approvedEntityIds = approvedEvents.stream()
            .map(AnalyticsEvent::getEntityId)
            .toList();

        var createdEvents = approvedEntityIds.isEmpty()
            ? List.<AnalyticsEvent>of()
            : analyticsEventRepository.findByEntityIdInAndEventType(
                approvedEntityIds, AppConstants.EVENT_SPEC_CREATED);

        var createdByEntityId = createdEvents.stream()
            .collect(Collectors.toMap(AnalyticsEvent::getEntityId, Function.identity(), (a, b) -> a));

        // Calculate cycle times
        var cycleTimes = new ArrayList<Double>();
        for (var approved : approvedEvents) {
            var created = createdByEntityId.get(approved.getEntityId());
            if (created != null) {
                var hours = Duration.between(created.getEventTimestamp(), approved.getEventTimestamp()).toMinutes() / 60.0;
                cycleTimes.add(hours);
            }
        }

        double avgCycleHours = cycleTimes.isEmpty() ? 0.0
            : cycleTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // Weekly trend
        var oneWeekAgo = Instant.now().minus(Duration.ofDays(7));
        int approvedThisWeek = (int) approvedEvents.stream()
            .filter(e -> e.getEventTimestamp().isAfter(oneWeekAgo))
            .count();

        var weeklyTrend = approvedEvents.stream()
            .collect(Collectors.groupingBy(
                e -> e.getEventTimestamp().atZone(ZoneOffset.UTC).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                    + "-" + e.getEventTimestamp().atZone(ZoneOffset.UTC).getYear(),
                Collectors.counting()))
            .entrySet().stream()
            .map(entry -> new WeeklyCount(entry.getKey(), entry.getValue().intValue()))
            .sorted(Comparator.comparing(WeeklyCount::week))
            .toList();

        var metric = new SpecificationVelocityMetric(
            Math.round(avgCycleHours * 100.0) / 100.0,
            approvedEvents.size(),
            totalRejected,
            approvedThisWeek,
            weeklyTrend
        );

        return ResponseEntity.ok(metric);
    }

    @GetMapping("/ticket-velocity")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'tv:' + #tenantId + ':' + #programId + ':' + #days")
    public ResponseEntity<TicketVelocityMetric> getTicketVelocity(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID programId,
            @RequestParam(defaultValue = "30") int days) {

        var since = Instant.now().minus(Duration.ofDays(days));

        // Fetch resolved events
        var resolvedEvents = analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_TICKET_RESOLVED, since);

        // Fetch created events for cross-reference
        var createdEvents = analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_TICKET_CREATED, since);

        // Also get all-time created events for open count and severity lookup
        var resolvedEntityIds = resolvedEvents.stream()
            .map(AnalyticsEvent::getEntityId)
            .collect(Collectors.toSet());

        var allCreatedByEntityId = new HashMap<UUID, AnalyticsEvent>();
        if (!resolvedEntityIds.isEmpty()) {
            analyticsEventRepository.findByEntityIdInAndEventType(
                new ArrayList<>(resolvedEntityIds), AppConstants.EVENT_TICKET_CREATED)
                .forEach(e -> allCreatedByEntityId.put(e.getEntityId(), e));
        }
        // Also include recently created events
        createdEvents.forEach(e -> allCreatedByEntityId.putIfAbsent(e.getEntityId(), e));

        // Filter by programId if needed (via workstream's parent program)
        // For now, filtering is based on available data

        // Count open tickets (created but not resolved in period)
        var createdEntityIds = createdEvents.stream()
            .map(AnalyticsEvent::getEntityId)
            .collect(Collectors.toSet());
        int totalOpen = (int) createdEntityIds.stream()
            .filter(id -> !resolvedEntityIds.contains(id))
            .count();

        // Calculate resolution times by severity
        var resolutionBySeverity = new HashMap<String, List<Double>>();
        var allResolutionHours = new ArrayList<Double>();

        for (var resolved : resolvedEvents) {
            var created = allCreatedByEntityId.get(resolved.getEntityId());
            if (created != null) {
                var hours = Duration.between(created.getEventTimestamp(), resolved.getEventTimestamp()).toMinutes() / 60.0;
                allResolutionHours.add(hours);

                var severity = getStringMeta(created, "severity");
                if (severity != null) {
                    resolutionBySeverity.computeIfAbsent(severity, k -> new ArrayList<>()).add(hours);
                }
            }
        }

        double avgResolution = allResolutionHours.isEmpty() ? 0.0
            : allResolutionHours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        var avgBySeverity = resolutionBySeverity.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> Math.round(e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 100.0) / 100.0
            ));

        var metric = new TicketVelocityMetric(
            Math.round(avgResolution * 100.0) / 100.0,
            resolvedEvents.size(),
            totalOpen,
            avgBySeverity
        );

        return ResponseEntity.ok(metric);
    }

    @GetMapping("/ticket-resolution-breakdown")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'trb:' + #tenantId + ':' + #programId + ':' + #days")
    public ResponseEntity<TicketResolutionBreakdown> getTicketResolutionBreakdown(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID programId,
            @RequestParam(defaultValue = "30") int days) {

        var since = Instant.now().minus(Duration.ofDays(days));

        var resolvedEvents = analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_TICKET_RESOLVED, since);

        // By resolution type
        var byResolution = new HashMap<String, Integer>();
        for (var event : resolvedEvents) {
            var resolution = getStringMeta(event, "resolution");
            if (resolution != null) {
                byResolution.merge(resolution, 1, Integer::sum);
            }
        }

        // Get created events for type and severity
        var resolvedEntityIds = resolvedEvents.stream()
            .map(AnalyticsEvent::getEntityId)
            .toList();

        var createdEvents = resolvedEntityIds.isEmpty()
            ? List.<AnalyticsEvent>of()
            : analyticsEventRepository.findByEntityIdInAndEventType(
                resolvedEntityIds, AppConstants.EVENT_TICKET_CREATED);

        var byType = new HashMap<String, Integer>();
        var bySeverity = new HashMap<String, Integer>();

        for (var created : createdEvents) {
            var type = getStringMeta(created, "type");
            if (type != null) {
                byType.merge(type, 1, Integer::sum);
            }
            var severity = getStringMeta(created, "severity");
            if (severity != null) {
                bySeverity.merge(severity, 1, Integer::sum);
            }
        }

        return ResponseEntity.ok(new TicketResolutionBreakdown(byResolution, byType, bySeverity));
    }

    @GetMapping("/ai-vs-human-resolution")
    @Cacheable(value = AppConstants.CACHE_METRICS, key = "'avh:' + #tenantId + ':' + #days")
    public ResponseEntity<AiVsHumanMetric> getAiVsHumanResolution(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "30") int days) {

        var since = Instant.now().minus(Duration.ofDays(days));

        // Fetch resolved tickets
        var resolvedEvents = analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_TICKET_RESOLVED, since);

        // Fetch created tickets for workstreamId
        var resolvedEntityIds = resolvedEvents.stream()
            .map(AnalyticsEvent::getEntityId)
            .toList();

        var createdByEntityId = resolvedEntityIds.isEmpty()
            ? Map.<UUID, AnalyticsEvent>of()
            : analyticsEventRepository.findByEntityIdInAndEventType(
                resolvedEntityIds, AppConstants.EVENT_TICKET_CREATED).stream()
                .collect(Collectors.toMap(AnalyticsEvent::getEntityId, Function.identity(), (a, b) -> a));

        // Fetch all workstream created events for execution mode lookup
        var workstreamEvents = analyticsEventRepository.findByTenantIdAndEventTypeAndEventTimestampAfter(
            tenantId, AppConstants.EVENT_WORKSTREAM_CREATED, Instant.EPOCH);

        var executionModeByWorkstream = workstreamEvents.stream()
            .collect(Collectors.toMap(
                AnalyticsEvent::getEntityId,
                e -> getStringMeta(e, "executionMode"),
                (a, b) -> b));

        // Group resolution times by execution mode
        var aiFirstHours = new ArrayList<Double>();
        var traditionalHours = new ArrayList<Double>();
        var hybridHours = new ArrayList<Double>();

        for (var resolved : resolvedEvents) {
            var created = createdByEntityId.get(resolved.getEntityId());
            if (created == null) continue;

            var workstreamIdStr = getStringMeta(created, "workstreamId");
            if (workstreamIdStr == null) continue;

            var workstreamId = UUID.fromString(workstreamIdStr);
            var executionMode = executionModeByWorkstream.get(workstreamId);
            if (executionMode == null) continue;

            var hours = Duration.between(created.getEventTimestamp(), resolved.getEventTimestamp()).toMinutes() / 60.0;

            switch (executionMode) {
                case "AI_FIRST" -> aiFirstHours.add(hours);
                case "TRADITIONAL" -> traditionalHours.add(hours);
                case "HYBRID" -> hybridHours.add(hours);
            }
        }

        double aiAvg = aiFirstHours.isEmpty() ? 0.0
            : aiFirstHours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double tradAvg = traditionalHours.isEmpty() ? 0.0
            : traditionalHours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double hybridAvg = hybridHours.isEmpty() ? 0.0
            : hybridHours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double speedup = (aiAvg > 0 && tradAvg > 0) ? tradAvg / aiAvg : 0.0;

        var metric = new AiVsHumanMetric(
            Math.round(aiAvg * 100.0) / 100.0,
            Math.round(tradAvg * 100.0) / 100.0,
            Math.round(hybridAvg * 100.0) / 100.0,
            aiFirstHours.size(),
            traditionalHours.size(),
            hybridHours.size(),
            Math.round(speedup * 100.0) / 100.0
        );

        return ResponseEntity.ok(metric);
    }

    // ── Helper methods ───────────────────────────────────────────────

    private String getStringMeta(AnalyticsEvent event, String key) {
        if (event.getMetadata() == null || !event.getMetadata().containsKey(key)) return null;
        return event.getMetadata().get(key).toString();
    }

    private int getIntDimension(Map<String, Object> dimensions, String key) {
        if (dimensions == null || !dimensions.containsKey(key)) return 0;
        return ((Number) dimensions.get(key)).intValue();
    }

    private double getDoubleDimension(Map<String, Object> dimensions, String key) {
        if (dimensions == null || !dimensions.containsKey(key)) return 0.0;
        return ((Number) dimensions.get(key)).doubleValue();
    }
}
