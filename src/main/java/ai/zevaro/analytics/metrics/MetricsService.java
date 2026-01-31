package ai.zevaro.analytics.metrics;

import ai.zevaro.analytics.config.AppConstants;
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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MetricSnapshotRepository snapshotRepository;
    private final DecisionCycleLogRepository cycleLogRepository;

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#tenantId")
    public void recordDecisionResolved(
            UUID tenantId,
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
        updateDailySnapshot(tenantId, resolvedAt.atZone(ZoneOffset.UTC).toLocalDate());

        log.debug("Recorded decision cycle: {}h for decision {}", cycleTimeHours, decisionId);
    }

    @Transactional
    @CacheEvict(value = AppConstants.CACHE_DASHBOARD, key = "#tenantId")
    public void recordOutcomeValidated(
            UUID tenantId,
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
                .metricType(AppConstants.METRIC_OUTCOME_VELOCITY)
                .metricDate(today)
                .value(BigDecimal.ONE)
                .dimensions(Map.of())
                .build();
            snapshotRepository.save(snapshot);
        }

        log.debug("Recorded outcome validation: {}", outcomeId);
    }

    private void updateDailySnapshot(UUID tenantId, LocalDate date) {
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
