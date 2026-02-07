package ai.zevaro.analytics.insights;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.insights.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(AppConstants.API_V1 + "/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;

    @GetMapping
    public ResponseEntity<List<Insight>> getInsights(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID projectId) {
        return ResponseEntity.ok(insightsService.generateInsights(tenantId, projectId));
    }

    @GetMapping("/trends")
    public ResponseEntity<List<Trend>> getTrends(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID projectId) {
        return ResponseEntity.ok(insightsService.detectTrends(tenantId, projectId));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<String>> getRecommendations(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID projectId) {
        return ResponseEntity.ok(insightsService.getRecommendations(tenantId, projectId));
    }
}
