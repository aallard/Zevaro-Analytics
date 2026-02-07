package ai.zevaro.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DecisionCycleLogRepository extends JpaRepository<DecisionCycleLog, UUID> {

    List<DecisionCycleLog> findByTenantIdAndResolvedAtBetween(
        UUID tenantId, Instant start, Instant end);

    @Query("SELECT AVG(d.cycleTimeHours) FROM DecisionCycleLog d " +
           "WHERE d.tenantId = :tenantId AND d.resolvedAt >= :since")
    Double findAvgCycleTimeSince(
        @Param("tenantId") UUID tenantId,
        @Param("since") Instant since);

    @Query("SELECT COUNT(d) FROM DecisionCycleLog d " +
           "WHERE d.tenantId = :tenantId AND d.wasEscalated = true AND d.resolvedAt >= :since")
    Long countEscalatedSince(
        @Param("tenantId") UUID tenantId,
        @Param("since") Instant since);

    @Query("SELECT d.stakeholderId, AVG(d.cycleTimeHours) FROM DecisionCycleLog d " +
           "WHERE d.tenantId = :tenantId AND d.resolvedAt >= :since " +
           "GROUP BY d.stakeholderId ORDER BY AVG(d.cycleTimeHours) ASC")
    List<Object[]> findAvgCycleTimeByStakeholder(
        @Param("tenantId") UUID tenantId,
        @Param("since") Instant since);

    List<DecisionCycleLog> findByTenantIdAndProjectId(UUID tenantId, UUID projectId);

    @Query("SELECT AVG(d.cycleTimeHours) FROM DecisionCycleLog d " +
           "WHERE d.tenantId = :tenantId AND d.projectId = :projectId AND d.resolvedAt >= :since")
    Double findAvgCycleTimeSinceByProject(
        @Param("tenantId") UUID tenantId,
        @Param("projectId") UUID projectId,
        @Param("since") Instant since);

    @Query("SELECT COUNT(d) FROM DecisionCycleLog d " +
           "WHERE d.tenantId = :tenantId AND d.projectId = :projectId AND d.wasEscalated = true AND d.resolvedAt >= :since")
    Long countEscalatedSinceByProject(
        @Param("tenantId") UUID tenantId,
        @Param("projectId") UUID projectId,
        @Param("since") Instant since);
}
