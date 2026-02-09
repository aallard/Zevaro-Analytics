package ai.zevaro.analytics.metrics;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.consumer.events.*;
import ai.zevaro.analytics.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MetricSnapshotRepository snapshotRepository;
    private final DecisionCycleLogRepository cycleLogRepository;
    private final AnalyticsEventRepository analyticsEventRepository;

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#tenantId")
    public void recordDecisionResolved(
            UUID tenantId,
            UUID projectId,
            UUID decisionId,
            Instant createdAt,
            Instant resolvedAt,
            String priority,
            String decisionType,
            boolean wasEscalated,
            UUID stakeholderId) {

        // Calculate cycle time
        var cycleTimeHours = Duration.between(createdAt, resolvedAt).toMinutes() / 60.0;

        // Log the individual decision
        var cycleLog = DecisionCycleLog.builder()
            .tenantId(tenantId)
            .projectId(projectId)
            .decisionId(decisionId)
            .createdAt(createdAt)
            .resolvedAt(resolvedAt)
            .cycleTimeHours(BigDecimal.valueOf(cycleTimeHours).setScale(2, RoundingMode.HALF_UP))
            .priority(priority)
            .decisionType(decisionType)
            .wasEscalated(wasEscalated)
            .stakeholderId(stakeholderId)
            .build();

        cycleLogRepository.save(cycleLog);

        // Update daily snapshot
        updateDailySnapshot(tenantId, projectId, resolvedAt.atZone(ZoneOffset.UTC).toLocalDate());

        log.debug("Recorded decision cycle: {}h for decision {}", cycleTimeHours, decisionId);
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#tenantId")
    public void recordOutcomeValidated(
            UUID tenantId,
            UUID projectId,
            UUID outcomeId,
            Instant createdAt,
            Instant validatedAt) {

        // Update outcome velocity metric
        var today = validatedAt.atZone(ZoneOffset.UTC).toLocalDate();
        var existing = snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, today);

        if (existing.isPresent()) {
            var snapshot = existing.get();
            snapshot.setValue(snapshot.getValue().add(BigDecimal.ONE));
            snapshotRepository.save(snapshot);
        } else {
            var snapshot = MetricSnapshot.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
                .metricDate(today)
                .value(BigDecimal.ONE)
                .dimensions(Map.of())
                .build();
            snapshotRepository.save(snapshot);
        }

        log.debug("Recorded outcome validation: {}", outcomeId);
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#tenantId")
    public void recordHypothesisConcluded(
            UUID tenantId,
            UUID projectId,
            UUID hypothesisId,
            UUID outcomeId,
            String result,  // VALIDATED or INVALIDATED
            Instant createdAt,
            Instant concludedAt) {

        var today = concludedAt.atZone(ZoneOffset.UTC).toLocalDate();
        var existing = snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            tenantId, AppConstants.METRIC_HYPOTHESIS_THROUGHPUT, today);

        if (existing.isPresent()) {
            var snapshot = existing.get();
            snapshot.setValue(snapshot.getValue().add(BigDecimal.ONE));

            // Update dimensions
            var dims = snapshot.getDimensions() != null
                ? new HashMap<>(snapshot.getDimensions())
                : new HashMap<String, Object>();

            int validated = ((Number) dims.getOrDefault("validated", 0)).intValue();
            int invalidated = ((Number) dims.getOrDefault("invalidated", 0)).intValue();

            if ("VALIDATED".equals(result)) {
                dims.put("validated", validated + 1);
            } else {
                dims.put("invalidated", invalidated + 1);
            }

            snapshot.setDimensions(dims);
            snapshotRepository.save(snapshot);
        } else {
            var dims = new HashMap<String, Object>();
            dims.put("validated", "VALIDATED".equals(result) ? 1 : 0);
            dims.put("invalidated", "INVALIDATED".equals(result) ? 1 : 0);

            var snapshot = MetricSnapshot.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .metricType(AppConstants.METRIC_HYPOTHESIS_THROUGHPUT)
                .metricDate(today)
                .value(BigDecimal.ONE)
                .dimensions(dims)
                .build();
            snapshotRepository.save(snapshot);
        }

        log.debug("Recorded hypothesis conclusion: {} - {}", hypothesisId, result);
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#tenantId")
    public void recordOutcomeInvalidated(
            UUID tenantId,
            UUID projectId,
            UUID outcomeId,
            Instant createdAt,
            Instant invalidatedAt) {

        var today = invalidatedAt.atZone(ZoneOffset.UTC).toLocalDate();
        var existing = snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            tenantId, AppConstants.METRIC_OUTCOME_VELOCITY, today);

        if (existing.isPresent()) {
            var snapshot = existing.get();
            var dims = snapshot.getDimensions() != null
                ? new HashMap<>(snapshot.getDimensions())
                : new HashMap<String, Object>();
            int invalidated = ((Number) dims.getOrDefault("invalidated", 0)).intValue();
            dims.put("invalidated", invalidated + 1);
            snapshot.setDimensions(dims);
            snapshotRepository.save(snapshot);
        } else {
            var dims = new HashMap<String, Object>();
            dims.put("invalidated", 1);
            dims.put("validated", 0);

            var snapshot = MetricSnapshot.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
                .metricDate(today)
                .value(BigDecimal.ZERO)
                .dimensions(dims)
                .build();
            snapshotRepository.save(snapshot);
        }

        log.debug("Recorded outcome invalidation: {}", outcomeId);
    }

    // ── Program events ────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordProgramCreated(ProgramCreatedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_PROGRAM_CREATED)
            .entityId(event.programId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "name", event.name(),
                "status", event.status(),
                "createdById", event.createdById().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded program created: {}", event.programId());
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordProgramStatusChanged(ProgramStatusChangedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_PROGRAM_STATUS_CHANGED)
            .entityId(event.programId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "oldStatus", event.oldStatus(),
                "newStatus", event.newStatus(),
                "changedById", event.changedById().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded program status changed: {} {} -> {}",
            event.programId(), event.oldStatus(), event.newStatus());
    }

    // ── Workstream events ────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordWorkstreamCreated(WorkstreamCreatedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_WORKSTREAM_CREATED)
            .entityId(event.workstreamId())
            .parentId(event.programId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "name", event.name(),
                "mode", event.mode(),
                "executionMode", event.executionMode(),
                "createdById", event.createdById().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded workstream created: {}", event.workstreamId());
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordWorkstreamStatusChanged(WorkstreamStatusChangedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_WORKSTREAM_STATUS_CHANGED)
            .entityId(event.workstreamId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "oldStatus", event.oldStatus(),
                "newStatus", event.newStatus(),
                "changedById", event.changedById().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded workstream status changed: {} {} -> {}",
            event.workstreamId(), event.oldStatus(), event.newStatus());
    }

    // ── Specification events ─────────────────────────────────────────

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordSpecificationCreated(SpecificationCreatedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_SPEC_CREATED)
            .entityId(event.specificationId())
            .parentId(event.programId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "name", event.name(),
                "workstreamId", event.workstreamId().toString(),
                "authorId", event.authorId().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded specification created: {}", event.specificationId());
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordSpecificationStatusChanged(SpecificationStatusChangedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_SPEC_STATUS_CHANGED)
            .entityId(event.specificationId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "oldStatus", event.oldStatus(),
                "newStatus", event.newStatus(),
                "changedById", event.changedById().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded specification status changed: {} {} -> {}",
            event.specificationId(), event.oldStatus(), event.newStatus());
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordSpecificationApproved(SpecificationApprovedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_SPEC_APPROVED)
            .entityId(event.specificationId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "approvedById", event.approvedById().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded specification approved: {}", event.specificationId());
    }

    // ── Ticket events ────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordTicketCreated(TicketCreatedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_TICKET_CREATED)
            .entityId(event.ticketId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "workstreamId", event.workstreamId().toString(),
                "type", event.type(),
                "severity", event.severity(),
                "reportedById", event.reportedById().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded ticket created: {}", event.ticketId());
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordTicketResolved(TicketResolvedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_TICKET_RESOLVED)
            .entityId(event.ticketId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "resolution", event.resolution(),
                "resolvedById", event.resolvedById().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded ticket resolved: {}", event.ticketId());
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#event.tenantId()")
    public void recordTicketAssigned(TicketAssignedEvent event) {
        var ae = AnalyticsEvent.builder()
            .tenantId(event.tenantId())
            .eventType(AppConstants.EVENT_TICKET_ASSIGNED)
            .entityId(event.ticketId())
            .eventTimestamp(event.timestamp())
            .metadata(Map.of(
                "assignedToId", event.assignedToId().toString(),
                "assignedById", event.assignedById().toString()))
            .build();
        analyticsEventRepository.save(ae);
        log.debug("Recorded ticket assigned: {}", event.ticketId());
    }

    // ── Existing private methods ─────────────────────────────────────

    private void updateDailySnapshot(UUID tenantId, UUID projectId, LocalDate date) {
        var startOfDay = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        var endOfDay = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        // Calculate average cycle time for the day
        var logs = cycleLogRepository.findByTenantIdAndResolvedAtBetween(
            tenantId, startOfDay, endOfDay);

        if (logs.isEmpty()) return;

        var avgCycleTime = logs.stream()
            .map(DecisionCycleLog::getCycleTimeHours)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(logs.size()), 2, RoundingMode.HALF_UP);

        var escalatedCount = logs.stream().filter(DecisionCycleLog::getWasEscalated).count();

        var existing = snapshotRepository.findByTenantIdAndMetricTypeAndMetricDate(
            tenantId, AppConstants.METRIC_DECISION_VELOCITY, date);

        var snapshot = existing.orElse(MetricSnapshot.builder()
            .tenantId(tenantId)
            .projectId(projectId)
            .metricType(AppConstants.METRIC_DECISION_VELOCITY)
            .metricDate(date)
            .build());

        snapshot.setValue(avgCycleTime);
        snapshot.setDimensions(Map.of(
            "decisionsResolved", logs.size(),
            "escalatedCount", escalatedCount,
            "escalationRate", logs.isEmpty() ? 0 : (double) escalatedCount / logs.size()
        ));

        snapshotRepository.save(snapshot);
    }
}
