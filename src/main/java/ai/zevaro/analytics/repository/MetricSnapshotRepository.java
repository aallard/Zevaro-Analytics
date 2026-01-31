package ai.zevaro.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, UUID> {

    Optional<MetricSnapshot> findByTenantIdAndMetricTypeAndMetricDate(
        UUID tenantId, String metricType, LocalDate metricDate);

    List<MetricSnapshot> findByTenantIdAndMetricTypeAndMetricDateBetweenOrderByMetricDateAsc(
        UUID tenantId, String metricType, LocalDate startDate, LocalDate endDate);

    @Query("SELECT m FROM MetricSnapshot m WHERE m.tenantId = :tenantId " +
           "AND m.metricType = :metricType ORDER BY m.metricDate DESC LIMIT :limit")
    List<MetricSnapshot> findRecentByTenantAndType(
        @Param("tenantId") UUID tenantId,
        @Param("metricType") String metricType,
        @Param("limit") int limit);
}
