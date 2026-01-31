package ai.zevaro.analytics.dashboard;

import ai.zevaro.analytics.config.AppConstants;
import ai.zevaro.analytics.dashboard.dto.DashboardData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(AppConstants.API_V1 + "/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardData> getDashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return ResponseEntity.ok(dashboardService.getDashboard(tenantId));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return ResponseEntity.ok(dashboardService.getDashboardSummary(tenantId));
    }
}
