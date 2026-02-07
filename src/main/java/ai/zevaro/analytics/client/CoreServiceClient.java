package ai.zevaro.analytics.client;

import ai.zevaro.analytics.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REST client for Zevaro Core service.
 * Fetches live data (pending decisions, stakeholder names, outcome details, etc.)
 * that Analytics cannot compute from its own local metrics tables.
 *
 * RATE-LIMITED LOGGING: When Core is unreachable, logs one error on first failure
 * then suppresses further warnings for 5 minutes to prevent log storms.
 * A dashboard request can trigger 5-7 Core calls — without rate limiting,
 * a Core outage + user traffic produces thousands of log.warn per minute.
 */
@Component
@Slf4j
public class CoreServiceClient {

    private final RestTemplate restTemplate;
    private final String coreServiceUrl;

    // Rate-limited logging state — prevents log storms when Core is down
    private final AtomicReference<Instant> lastErrorLog = new AtomicReference<>(Instant.EPOCH);
    private final AtomicInteger suppressedCount = new AtomicInteger(0);
    private final AtomicReference<Instant> firstFailure = new AtomicReference<>(null);
    private static final Duration ERROR_LOG_INTERVAL = Duration.ofMinutes(5);

    public CoreServiceClient(
            RestTemplate restTemplate,
            @Value("${services.core.url}") String coreServiceUrl) {
        this.restTemplate = restTemplate;
        this.coreServiceUrl = coreServiceUrl;
    }

    /**
     * Rate-limited error logging for Core service failures.
     * Logs immediately on first failure, then at most once every 5 minutes.
     * Tracks and reports the number of suppressed warnings.
     */
    private void logCoreFailure(String operation, String errorMessage) {
        Instant now = Instant.now();
        firstFailure.compareAndSet(null, now);
        Instant lastLog = lastErrorLog.get();

        if (Duration.between(lastLog, now).compareTo(ERROR_LOG_INTERVAL) > 0) {
            int suppressed = suppressedCount.getAndSet(0);
            if (suppressed > 0) {
                log.warn("Core service UNAVAILABLE — {} failed. {} warnings suppressed in last interval. Error: {}",
                    operation, suppressed, errorMessage);
            } else {
                log.warn("Core service UNAVAILABLE — {} failed. Error: {}", operation, errorMessage);
            }
            lastErrorLog.set(now);
        } else {
            suppressedCount.incrementAndGet();
        }
    }

    /**
     * Called when a Core request succeeds after a failure period.
     * Logs recovery and resets rate-limit state.
     */
    private void logCoreRecovery() {
        Instant failure = firstFailure.getAndSet(null);
        if (failure != null) {
            int suppressed = suppressedCount.getAndSet(0);
            Duration downtime = Duration.between(failure, Instant.now());
            log.info("Core service RESTORED after {}. {} warnings were suppressed during outage.",
                formatDuration(downtime), suppressed);
        }
    }

    private String formatDuration(Duration d) {
        long mins = d.toMinutes();
        long secs = d.toSecondsPart();
        return mins > 0 ? String.format("%dm %ds", mins, secs) : String.format("%ds", secs);
    }

    /**
     * Get count of decisions with status NEEDS_INPUT or UNDER_DISCUSSION.
     */
    public int getPendingDecisionCount(UUID tenantId) {
        try {
            var uri = URI.create(coreServiceUrl + "/api/v1/decisions?status=NEEDS_INPUT,UNDER_DISCUSSION&size=0");
            var headers = buildHeaders(tenantId);
            var request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            var response = restTemplate.exchange(request, new ParameterizedTypeReference<CorePageResponse>() {});
            var body = response.getBody();
            logCoreRecovery();
            return body != null ? body.totalElements() : 0;
        } catch (RestClientException e) {
            logCoreFailure("getPendingDecisionCount", e.getMessage());
            return 0;
        }
    }

    /**
     * Get urgent decisions (BLOCKING priority, NEEDS_INPUT status).
     */
    public List<CoreDecisionSummary> getUrgentDecisions(UUID tenantId) {
        try {
            var uri = URI.create(coreServiceUrl + "/api/v1/decisions?priority=BLOCKING&status=NEEDS_INPUT");
            var headers = buildHeaders(tenantId);
            var request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            var response = restTemplate.exchange(request, new ParameterizedTypeReference<CoreListResponse<CoreDecisionSummary>>() {});
            var body = response.getBody();
            logCoreRecovery();
            return body != null ? body.content() : List.of();
        } catch (RestClientException e) {
            logCoreFailure("getUrgentDecisions", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get stakeholder details by ID.
     */
    public CoreStakeholderInfo getStakeholder(UUID tenantId, UUID stakeholderId) {
        try {
            var uri = URI.create(coreServiceUrl + "/api/v1/stakeholders/" + stakeholderId);
            var headers = buildHeaders(tenantId);
            var request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            var response = restTemplate.exchange(request, CoreStakeholderInfo.class);
            logCoreRecovery();
            return response.getBody();
        } catch (RestClientException e) {
            logCoreFailure("getStakeholder(" + stakeholderId + ")", e.getMessage());
            return null;
        }
    }

    /**
     * Get outcome details by ID.
     */
    public CoreOutcomeInfo getOutcome(UUID tenantId, UUID outcomeId) {
        try {
            var uri = URI.create(coreServiceUrl + "/api/v1/outcomes/" + outcomeId);
            var headers = buildHeaders(tenantId);
            var request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            var response = restTemplate.exchange(request, CoreOutcomeInfo.class);
            logCoreRecovery();
            return response.getBody();
        } catch (RestClientException e) {
            logCoreFailure("getOutcome(" + outcomeId + ")", e.getMessage());
            return null;
        }
    }

    /**
     * Get count of hypotheses in BUILDING or DEPLOYED or MEASURING status.
     */
    public int getActiveHypothesisCount(UUID tenantId) {
        try {
            var uri = URI.create(coreServiceUrl + "/api/v1/hypotheses?status=BUILDING,DEPLOYED,MEASURING&size=0");
            var headers = buildHeaders(tenantId);
            var request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            var response = restTemplate.exchange(request, new ParameterizedTypeReference<CorePageResponse>() {});
            var body = response.getBody();
            logCoreRecovery();
            return body != null ? body.totalElements() : 0;
        } catch (RestClientException e) {
            logCoreFailure("getActiveHypothesisCount", e.getMessage());
            return 0;
        }
    }

    /**
     * Get count of hypotheses concluded this week.
     */
    public int getHypothesesTestedThisWeek(UUID tenantId) {
        try {
            var uri = URI.create(coreServiceUrl + "/api/v1/hypotheses?status=VALIDATED,INVALIDATED&size=0");
            var headers = buildHeaders(tenantId);
            var request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            var response = restTemplate.exchange(request, new ParameterizedTypeReference<CorePageResponse>() {});
            var body = response.getBody();
            logCoreRecovery();
            return body != null ? body.totalElements() : 0;
        } catch (RestClientException e) {
            logCoreFailure("getHypothesesTestedThisWeek", e.getMessage());
            return 0;
        }
    }

    /**
     * Get decisions created count for a tenant.
     */
    public int getDecisionsCreatedCount(UUID tenantId) {
        try {
            var uri = URI.create(coreServiceUrl + "/api/v1/decisions?size=0");
            var headers = buildHeaders(tenantId);
            var request = new RequestEntity<>(headers, HttpMethod.GET, uri);
            var response = restTemplate.exchange(request, new ParameterizedTypeReference<CorePageResponse>() {});
            var body = response.getBody();
            logCoreRecovery();
            return body != null ? body.totalElements() : 0;
        } catch (RestClientException e) {
            logCoreFailure("getDecisionsCreatedCount", e.getMessage());
            return 0;
        }
    }

    private HttpHeaders buildHeaders(UUID tenantId) {
        var headers = new HttpHeaders();
        headers.set("X-Tenant-Id", tenantId.toString());
        headers.set("Content-Type", "application/json");
        return headers;
    }
}
