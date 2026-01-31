# Zevaro-Analytics CONVENTIONS

> Analytics service for Zevaro - Metrics, Dashboards, Reports, AI Insights

---

## Service Overview

| Property | Value |
|----------|-------|
| Service Name | zevaro-analytics |
| Port (Backend) | 8081 |
| Base Package | `ai.zevaro.analytics` |
| Database | PostgreSQL (shared `zevaro` database) |
| Schema | `analytics` |

---

## Tech Stack

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 21 | LTS, virtual threads enabled |
| Spring Boot | 3.3.x | Latest stable |
| PostgreSQL | 16 | Time-series data, aggregations |
| Redis | 7.x | Caching computed metrics |
| Kafka | 3.x | Consuming events from Core |
| Elasticsearch | 8.x | Search, complex aggregations |
| Maven | 3.9+ | Build tool |

---

## Project Structure

```
zevaro-analytics/
├── src/main/java/ai/zevaro/analytics/
│   ├── ZevaroAnalyticsApplication.java
│   ├── config/
│   │   ├── AppConstants.java
│   │   ├── KafkaConsumerConfig.java
│   │   ├── ElasticsearchConfig.java
│   │   └── CacheConfig.java
│   ├── metrics/
│   │   ├── DecisionVelocityMetric.java
│   │   ├── OutcomeVelocityMetric.java
│   │   ├── StakeholderResponseMetric.java
│   │   ├── HypothesisThroughputMetric.java
│   │   ├── MetricsCalculator.java
│   │   └── MetricsController.java
│   ├── dashboard/
│   │   ├── DashboardData.java
│   │   ├── DashboardService.java
│   │   └── DashboardController.java
│   ├── reports/
│   │   ├── WeeklyDigestReport.java
│   │   ├── OutcomeReport.java
│   │   ├── ReportGenerator.java
│   │   └── ReportController.java
│   ├── insights/
│   │   ├── InsightsEngine.java
│   │   ├── TrendDetector.java
│   │   ├── RecommendationService.java
│   │   └── InsightsController.java
│   ├── consumer/
│   │   ├── DecisionEventConsumer.java
│   │   ├── OutcomeEventConsumer.java
│   │   └── HypothesisEventConsumer.java
│   └── repository/
│       ├── MetricSnapshotRepository.java
│       └── ReportRepository.java
├── src/main/resources/
│   ├── application.yml
│   └── application-dev.yml
├── pom.xml
├── Dockerfile
└── CONVENTIONS.md
```

---

## API Paths

```
Base URL: /api/v1

Dashboard:
  GET    /api/v1/dashboard                    # Main dashboard data
  GET    /api/v1/dashboard/summary            # Quick summary (for mobile)

Metrics:
  GET    /api/v1/metrics/decision-velocity    # Decision cycle time over period
  GET    /api/v1/metrics/outcome-velocity     # Outcomes validated over period
  GET    /api/v1/metrics/stakeholder-response # Response times by stakeholder
  GET    /api/v1/metrics/hypothesis-throughput # Experiments per period
  GET    /api/v1/metrics/pipeline-idle-time   # Time waiting for product input

Reports:
  GET    /api/v1/reports                      # List available reports
  GET    /api/v1/reports/weekly-digest        # Weekly digest report
  GET    /api/v1/reports/outcome/{outcomeId}  # Outcome-specific report
  POST   /api/v1/reports/generate             # Generate custom report

Insights:
  GET    /api/v1/insights                     # AI-generated insights
  GET    /api/v1/insights/trends              # Detected trends
  GET    /api/v1/insights/recommendations     # Actionable recommendations

Internal (called by Core service):
  POST   /api/v1/internal/metrics/decision-resolved
  POST   /api/v1/internal/metrics/outcome-validated
  POST   /api/v1/internal/metrics/hypothesis-concluded
```

---

## Key Metrics Definitions

### Decision Velocity (Primary)

```java
public record DecisionVelocityMetric(
    UUID tenantId,
    LocalDate date,
    double avgCycleTimeHours,      // Avg time from created -> resolved
    int decisionsCreated,
    int decisionsResolved,
    int decisionsEscalated,
    double escalationRate,          // escalated / resolved
    Map<String, Double> byPriority, // Breakdown by priority
    Map<String, Double> byType      // Breakdown by decision type
) {}
```

### Outcome Velocity

```java
public record OutcomeVelocityMetric(
    UUID tenantId,
    LocalDate date,
    int outcomesValidated,
    int outcomesInvalidated,
    int outcomesInProgress,
    double avgTimeToValidationDays
) {}
```

### Stakeholder Response Time

```java
public record StakeholderResponseMetric(
    UUID stakeholderId,
    String stakeholderName,
    double avgResponseTimeHours,
    int decisionsPending,
    int decisionsCompleted,
    double escalationRate,
    LocalDate periodStart,
    LocalDate periodEnd
) {}
```

---

## Kafka Consumers

Consumed topics from Zevaro-Core:

```java
@KafkaListener(topics = "zevaro.core.decision.resolved")
public void onDecisionResolved(DecisionResolvedEvent event) {
    // Calculate cycle time
    var cycleTimeHours = Duration.between(event.createdAt(), event.resolvedAt()).toHours();
    
    // Record metric
    metricsService.recordDecisionCycleTime(
        event.tenantId(),
        event.decisionId(),
        cycleTimeHours,
        event.priority(),
        event.wasEscalated()
    );
}

@KafkaListener(topics = "zevaro.core.outcome.validated")
public void onOutcomeValidated(OutcomeValidatedEvent event) {
    metricsService.recordOutcomeValidation(
        event.tenantId(),
        event.outcomeId(),
        event.createdAt(),
        event.validatedAt()
    );
}
```

---

## Dashboard Data Structure

```java
public record DashboardData(
    // Summary cards
    int decisionsPendingCount,
    double avgDecisionWaitHours,
    int outcomesValidatedThisWeek,
    int hypothesesTestedThisWeek,
    int experimentsRunning,
    
    // Decision health
    String decisionHealthStatus,  // GREEN, YELLOW, RED
    List<DecisionSummary> urgentDecisions,
    
    // Charts data
    List<DataPoint> decisionVelocityTrend,    // Last 30 days
    List<DataPoint> outcomeVelocityTrend,     // Last 30 days
    List<StakeholderScore> stakeholderLeaderboard,
    
    // Pipeline status
    String pipelineStatus,        // IDLE, BUILDING, DEPLOYED
    Instant lastDeployment,
    Duration idleTime
) {}
```

---

## Caching Strategy

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        return RedisCacheManager.builder(factory)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)))  // 5-minute TTL
            .withCacheConfiguration("dashboard",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(1)))  // Dashboard: 1-minute TTL
            .withCacheConfiguration("reports",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(1)))    // Reports: 1-hour TTL
            .build();
    }
}

// Usage
@Service
public class DashboardService {
    
    @Cacheable(value = "dashboard", key = "#tenantId")
    public DashboardData getDashboard(UUID tenantId) {
        // Expensive computation
    }
    
    @CacheEvict(value = "dashboard", key = "#tenantId")
    public void invalidateDashboard(UUID tenantId) {
        // Called when new events arrive
    }
}
```

---

## AI Insights Engine

```java
@Service
public class InsightsEngine {
    
    public List<Insight> generateInsights(UUID tenantId) {
        var insights = new ArrayList<Insight>();
        
        // Trend detection
        var decisionTrend = detectTrend(getDecisionVelocity(tenantId, 30));
        if (decisionTrend.isSignificant()) {
            insights.add(new Insight(
                InsightType.TREND,
                decisionTrend.direction() == Direction.UP 
                    ? "Decision velocity improving" 
                    : "Decision velocity declining",
                decisionTrend.percentChange(),
                generateRecommendation(decisionTrend)
            ));
        }
        
        // Bottleneck detection
        var slowStakeholders = findSlowStakeholders(tenantId);
        if (!slowStakeholders.isEmpty()) {
            insights.add(new Insight(
                InsightType.BOTTLENECK,
                "Stakeholder response time bottleneck detected",
                slowStakeholders,
                "Consider escalation or delegation"
            ));
        }
        
        return insights;
    }
}
```

---

## Configuration

### application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: zevaro-analytics
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/zevaro
    username: ${DB_USER:zevaro}
    password: ${DB_PASSWORD:zevaro}
  jpa:
    hibernate:
      ddl-auto: ${DDL_AUTO:update}
    properties:
      hibernate:
        default_schema: analytics
  redis:
    host: ${REDIS_HOST:localhost}
    port: 6379
  kafka:
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
    consumer:
      group-id: zevaro-analytics
      auto-offset-reset: earliest
  elasticsearch:
    uris: ${ELASTICSEARCH_URI:http://localhost:9200}

services:
  core:
    url: ${CORE_SERVICE_URL:http://localhost:8080}
```

---

## Database Tables

```sql
-- Metric snapshots (time-series)
CREATE TABLE analytics.metric_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_date DATE NOT NULL,
    value DECIMAL(15,4) NOT NULL,
    dimensions JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, metric_type, metric_date, dimensions)
);

CREATE INDEX idx_metric_snapshots_tenant_date 
ON analytics.metric_snapshots(tenant_id, metric_type, metric_date);

-- Pre-computed reports
CREATE TABLE analytics.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    data JSONB NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Decision cycle time log (for detailed analysis)
CREATE TABLE analytics.decision_cycle_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NOT NULL,
    cycle_time_hours DECIMAL(10,2) NOT NULL,
    priority VARCHAR(20),
    decision_type VARCHAR(50),
    was_escalated BOOLEAN DEFAULT FALSE,
    stakeholder_id UUID
);

CREATE INDEX idx_decision_cycle_tenant_date 
ON analytics.decision_cycle_log(tenant_id, resolved_at);
```

---

## Development Workflow

### Local Startup

```bash
# Ensure Core service is running first
curl http://localhost:8080/actuator/health

# Start Analytics
./mvnw spring-boot:run -Dspring.profiles.active=dev

# Verify health
curl http://localhost:8081/actuator/health
```

---

## Checklist for New Metrics

- [ ] Define metric record/DTO
- [ ] Create Kafka consumer for source events
- [ ] Implement calculation logic
- [ ] Add to MetricSnapshotRepository
- [ ] Expose via API endpoint
- [ ] Add caching if expensive
- [ ] Include in dashboard if relevant
- [ ] Add to weekly digest report
- [ ] Committed immediately after working

---

## Kafka Integration Requirements

> **CRITICAL:** These settings are MANDATORY. A 278GB log file incident occurred due to improper Kafka configuration.

### The 278GB Log Incident (January 2026)

**Root Cause:** When Kafka is unavailable, Spring Kafka's default retry settings (50ms backoff) combined with multiple consumer threads created a log flooding scenario:
- 3 Kafka listeners × 3 concurrent threads = 9 retry loops
- Each loop logging at 50ms intervals = ~180 log entries/second
- Result: 278GB log file in hours, disk full, service crash

### Mandatory Consumer Settings

```yaml
spring:
  kafka:
    enabled: ${KAFKA_ENABLED:true}
    consumer:
      properties:
        reconnect.backoff.ms: 1000        # 1 second (NOT default 50ms)
        reconnect.backoff.max.ms: 60000   # 60 seconds max
        retry.backoff.ms: 1000
    listener:
      concurrency: 1  # CRITICAL: Reduced from 3 to prevent 9 retry loops
```

### KafkaConsumerConfig Requirements

```java
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfig {
    // Defensive settings in consumerConfigs():
    // - RECONNECT_BACKOFF_MS_CONFIG = 1000
    // - RECONNECT_BACKOFF_MAX_MS_CONFIG = 60000
    //
    // Error handler with exponential backoff:
    // factory.setCommonErrorHandler(new DefaultErrorHandler(
    //     new ExponentialBackOff(1000L, 2.0)  // 1s, 2s, 4s, 8s...
    // ));
}
```

### @KafkaListener Error Handling

**CRITICAL:** All @KafkaListener methods must have try-catch:

```java
@KafkaListener(topics = "zevaro.core.decision.resolved")
public void onDecisionResolved(DecisionResolvedEvent event) {
    try {
        // Process event
    } catch (DataIntegrityViolationException e) {
        log.warn("Duplicate event ignored: {}", event.decisionId());
    } catch (DataAccessException e) {
        log.error("Database error processing decision event: {}", e.getMessage());
        throw e;  // Rethrow for retry
    } catch (Exception e) {
        log.error("Unexpected error processing decision event: {}", e.getMessage(), e);
        // Don't rethrow - prevents infinite retry loops
    }
}
```

### Logback Configuration (MANDATORY)

```xml
<!-- Separate Kafka log file with strict size limits -->
<appender name="KAFKA_RATE_LIMITED" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_FILE}-kafka.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <maxFileSize>10MB</maxFileSize>
        <maxHistory>3</maxHistory>
        <totalSizeCap>50MB</totalSizeCap>
    </rollingPolicy>
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
        <level>WARN</level>
    </filter>
</appender>

<!-- Kafka client loggers - ERROR only, separate file -->
<logger name="org.apache.kafka" level="ERROR" additivity="false">
    <appender-ref ref="KAFKA_RATE_LIMITED"/>
</logger>
```

### KAFKA_ENABLED Toggle

For local development without Kafka:

```bash
export KAFKA_ENABLED=false
./mvnw spring-boot:run
```

When disabled, @ConditionalOnProperty prevents KafkaConsumerConfig bean creation.

### KafkaHealthIndicator

The service includes a KafkaHealthIndicator that:
- Tracks consecutive failures
- Rate-limits logging (max 1 log per minute when failing)
- Logs recovery when Kafka becomes available

### Checklist for Kafka Changes

- [ ] Verify reconnect.backoff.ms >= 1000 (NOT 50ms default)
- [ ] Verify listener concurrency = 1
- [ ] All @KafkaListener methods have try-catch
- [ ] Confirm logback-spring.xml has separate Kafka appender
- [ ] Test with Kafka DOWN before deploying
- [ ] Check KafkaHealthIndicator status in /actuator/health
