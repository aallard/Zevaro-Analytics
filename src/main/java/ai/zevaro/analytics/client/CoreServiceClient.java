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
import java.util.List;
import java.util.UUID;

/**
 * REST client for Zevaro Core service.
 * Fetches live data (pending decisions, stakeholder names, outcome details, etc.)
 * that Analytics cannot compute from its own local metrics tables.
 */
@Component
@Slf4j
public class CoreServiceClient {

    private final RestTemplate restTemplate;
    private final String coreServiceUrl;

    public CoreServiceClient(
            RestTemplate restTemplate,
            @Value("${services.core.url}") String coreServiceUrl) {
        this.restTemplate = restTemplate;
        this.coreServiceUrl = coreServiceUrl;
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
            return body != null ? body.totalElements() : 0;
        } catch (RestClientException e) {
            log.warn("Failed to fetch pending decision count from Core service: {}", e.getMessage());
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
            return body != null ? body.content() : List.of();
        } catch (RestClientException e) {
            log.warn("Failed to fetch urgent decisions from Core service: {}", e.getMessage());
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
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("Failed to fetch stakeholder {} from Core service: {}", stakeholderId, e.getMessage());
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
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("Failed to fetch outcome {} from Core service: {}", outcomeId, e.getMessage());
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
            return body != null ? body.totalElements() : 0;
        } catch (RestClientException e) {
            log.warn("Failed to fetch active hypothesis count from Core service: {}", e.getMessage());
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
            return body != null ? body.totalElements() : 0;
        } catch (RestClientException e) {
            log.warn("Failed to fetch hypothesis tested count from Core service: {}", e.getMessage());
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
            return body != null ? body.totalElements() : 0;
        } catch (RestClientException e) {
            log.warn("Failed to fetch decisions created count from Core service: {}", e.getMessage());
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
