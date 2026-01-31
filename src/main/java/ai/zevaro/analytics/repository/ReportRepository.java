package ai.zevaro.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByTenantIdAndReportTypeAndPeriodStartAndPeriodEnd(
        UUID tenantId, String reportType, LocalDate periodStart, LocalDate periodEnd);

    List<Report> findByTenantIdAndReportTypeOrderByGeneratedAtDesc(
        UUID tenantId, String reportType);

    List<Report> findByTenantIdOrderByGeneratedAtDesc(UUID tenantId);
}
