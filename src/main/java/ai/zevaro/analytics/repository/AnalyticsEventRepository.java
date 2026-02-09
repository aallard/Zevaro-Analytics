package ai.zevaro.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    List<AnalyticsEvent> findByTenantIdAndEventTypeAndEventTimestampAfter(
        UUID tenantId, String eventType, Instant since);

    List<AnalyticsEvent> findByTenantIdAndEventTypeAndParentIdAndEventTimestampAfter(
        UUID tenantId, String eventType, UUID parentId, Instant since);

    List<AnalyticsEvent> findByEntityIdInAndEventType(
        Collection<UUID> entityIds, String eventType);

    Optional<AnalyticsEvent> findFirstByEntityIdAndEventType(
        UUID entityId, String eventType);
}
