# Zevaro-Analytics Comprehensive Audit

**Audit Date:** 2026-02-08
**Project Version:** 1.0.0
**Repository:** /Users/adamallard/Documents/GitHub/Zevaro-Analytics

---

## 1. Project Overview

Zevaro-Analytics is a standalone analytics microservice for the Zevaro COE (Center of Excellence) Platform. It consumes domain events from the Zevaro-Core service via Kafka, computes metrics (decision velocity, outcome velocity, stakeholder response times, hypothesis throughput), persists time-series snapshots, and exposes dashboards, reports, and AI-generated insights through a REST API.

| Property | Value |
|---|---|
| Group ID | `ai.zevaro` |
| Artifact ID | `zevaro-analytics` |
| Version | `1.0.0` |
| Language | Java 21 (configured in pom.xml; runtime Docker image uses Eclipse Temurin 21) |
| Framework | Spring Boot 3.3.0 |
| Build Tool | Maven 3.9+ (Maven wrapper included) |
| Server Port | 8081 |
| Base Package | `ai.zevaro.analytics` |
| License | AGPL-3.0-or-later (dual-licensed with commercial option) |

---

## 2. Architecture

### 2.1 Package Structure

```
ai.zevaro.analytics/
  ZevaroAnalyticsApplication.java         # Entry point (@SpringBootApplication, @EnableCaching, @EnableKafka, @EnableScheduling)

  config/
    AppConstants.java                      # Static constants (API paths, Kafka topics, cache names, metric types)
    CacheConfig.java                       # Redis cache manager with per-cache TTL settings
    KafkaConsumerConfig.java               # Kafka consumer factory with defensive backoff settings
    KafkaHealthIndicator.java              # Custom actuator health indicator for Kafka with rate-limited logging
    RestTemplateConfig.java                # RestTemplate bean with 5s connect / 10s read timeouts

  client/
    CoreServiceClient.java                 # REST client for Zevaro-Core (fetches live data: decisions, stakeholders, outcomes, hypotheses)
    dto/
      CorePageResponse.java                # Paginated response wrapper from Core
      CoreListResponse.java                # List response wrapper from Core
      CoreDecisionSummary.java             # Decision summary DTO from Core
      CoreStakeholderInfo.java             # Stakeholder info DTO from Core
      CoreOutcomeInfo.java                 # Outcome info DTO from Core

  consumer/
    DecisionEventConsumer.java             # Kafka listener for decision.resolved events
    OutcomeEventConsumer.java              # Kafka listener for outcome.validated events
    OutcomeInvalidatedEventConsumer.java   # Kafka listener for outcome.invalidated events
    HypothesisEventConsumer.java           # Kafka listener for hypothesis.concluded events
    RateLimitedConsumerLogger.java         # Shared utility for rate-limited logging across consumers
    events/
      DecisionResolvedEvent.java           # Inbound Kafka event record
      OutcomeValidatedEvent.java           # Inbound Kafka event record
      OutcomeInvalidatedEvent.java         # Inbound Kafka event record
      HypothesisConcludedEvent.java        # Inbound Kafka event record

  metrics/
    MetricsService.java                    # Business logic for recording metric events into DB
    MetricsController.java                 # REST controller for reading metrics
    dto/
      DecisionVelocityMetric.java          # Response DTO
      OutcomeVelocityMetric.java           # Response DTO (defined but not used in controller; outcome-velocity returns Map)
      StakeholderResponseMetric.java       # Response DTO
      HypothesisThroughputMetric.java      # Response DTO

  dashboard/
    DashboardController.java               # REST controller for dashboard endpoints
    DashboardService.java                  # Aggregates data from repositories + Core for dashboard
    dto/
      DashboardData.java                   # Main dashboard response record
      DataPoint.java                       # (date, value) pair for charts
      DecisionSummary.java                 # Urgent decision summary
      StakeholderScore.java                # Stakeholder leaderboard entry

  reports/
    ReportController.java                  # REST controller for report endpoints
    ReportService.java                     # Generates and persists weekly digest and outcome reports
    dto/
      WeeklyDigestReport.java              # Weekly digest response record
      OutcomeReport.java                   # Outcome report response record
      TimelineEvent.java                   # Timeline entry within reports
      KeyResultProgress.java               # Key result progress (defined but not populated)

  insights/
    InsightsController.java                # REST controller for insights/trends/recommendations
    InsightsService.java                   # Detects trends, bottlenecks, achievements, generates recommendations
    dto/
      Insight.java                         # Insight response record
      InsightType.java                     # Enum: TREND, BOTTLENECK, ANOMALY, RECOMMENDATION, ACHIEVEMENT
      Trend.java                           # Trend response record
      TrendDirection.java                  # Enum: UP, DOWN, STABLE

  internal/
    InternalMetricsController.java         # Internal POST endpoints (alternative to Kafka for Core-to-Analytics metrics push)

  repository/
    MetricSnapshot.java                    # JPA entity for time-series metric snapshots
    MetricSnapshotRepository.java          # Spring Data JPA repository
    DecisionCycleLog.java                  # JPA entity for individual decision cycle time records
    DecisionCycleLogRepository.java        # Spring Data JPA repository with custom JPQL queries
    Report.java                            # JPA entity for persisted reports (JSONB data)
    ReportRepository.java                  # Spring Data JPA repository
```

### 2.2 Layered Architecture

The service follows a standard layered architecture:

1. **Controller Layer** -- REST endpoints (DashboardController, MetricsController, ReportController, InsightsController, InternalMetricsController)
2. **Service Layer** -- Business logic (DashboardService, MetricsService, ReportService, InsightsService)
3. **Consumer Layer** -- Kafka event consumers (DecisionEventConsumer, OutcomeEventConsumer, HypothesisEventConsumer, OutcomeInvalidatedEventConsumer)
4. **Client Layer** -- REST client to Core service (CoreServiceClient)
5. **Repository Layer** -- JPA repositories (MetricSnapshotRepository, DecisionCycleLogRepository, ReportRepository)
6. **Configuration Layer** -- Spring configs (CacheConfig, KafkaConsumerConfig, RestTemplateConfig, KafkaHealthIndicator, AppConstants)

### 2.3 Data Flow

```
Zevaro-Core ---[Kafka events]--> Consumer layer --> MetricsService --> Database (analytics schema)
                                                                    --> CacheEvict (Redis)

Zevaro-Core ---[REST /internal/metrics]--> InternalMetricsController --> MetricsService --> Database

Zevaro-Web ---[REST /api/v1/*]--> Controller layer --> Service layer --> Repository layer (local DB)
                                                                     --> CoreServiceClient (remote REST to Core)
                                                                     --> Redis cache
```

---

## 3. Dependencies

### 3.1 Spring Boot Starters (managed by parent 3.3.0)

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API (embedded Tomcat) |
| `spring-boot-starter-data-jpa` | JPA/Hibernate ORM |
| `spring-boot-starter-validation` | Bean validation |
| `spring-boot-starter-actuator` | Health, info, metrics endpoints |
| `spring-boot-starter-data-redis` | Redis connectivity |
| `spring-boot-starter-cache` | Caching abstraction |
| `spring-kafka` | Kafka consumer/listener |

### 3.2 Runtime Dependencies

| Dependency | Purpose |
|---|---|
| `postgresql` (runtime scope) | PostgreSQL JDBC driver |

### 3.3 Compile Dependencies

| Dependency | Purpose |
|---|---|
| `lombok` (optional) | Annotation-based boilerplate reduction |

### 3.4 Test Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-test` | JUnit 5, Mockito, MockMvc, AssertJ |
| `spring-kafka-test` | Kafka testing utilities |

### 3.5 Notable Missing Dependencies

- **Elasticsearch** -- Referenced in CONVENTIONS.md as part of the tech stack but no Elasticsearch dependency exists in pom.xml and no Elasticsearch configuration or code exists.
- **Spring Security** -- No security dependency at all. All endpoints are unauthenticated.
- **Flyway/Liquibase** -- No database migration tool. Schema managed by `hibernate.ddl-auto: update`.

---

## 4. Domain Model

### 4.1 JPA Entities

#### MetricSnapshot (table: analytics.metric_snapshots)

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | UUID | id | PK, auto-generated |
| tenantId | UUID | tenant_id | NOT NULL |
| projectId | UUID | project_id | nullable |
| metricType | String(50) | metric_type | NOT NULL |
| metricDate | LocalDate | metric_date | NOT NULL |
| value | BigDecimal(15,4) | value | NOT NULL |
| dimensions | Map<String,Object> | dimensions (JSONB) | nullable |
| createdAt | Instant | created_at | defaults to now |

**Unique constraint:** (tenant_id, metric_type, metric_date)
**Index:** idx_metric_tenant_type_date on (tenant_id, metric_type, metric_date)

#### DecisionCycleLog (table: analytics.decision_cycle_log)

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | UUID | id | PK, auto-generated |
| tenantId | UUID | tenant_id | NOT NULL |
| projectId | UUID | project_id | nullable |
| decisionId | UUID | decision_id | NOT NULL |
| createdAt | Instant | created_at | NOT NULL |
| resolvedAt | Instant | resolved_at | NOT NULL |
| cycleTimeHours | BigDecimal(10,2) | cycle_time_hours | NOT NULL |
| priority | String(20) | priority | nullable |
| decisionType | String(50) | decision_type | nullable |
| wasEscalated | Boolean | was_escalated | defaults to false |
| stakeholderId | UUID | stakeholder_id | nullable |

**Indexes:** idx_cycle_tenant_resolved on (tenant_id, resolved_at), idx_cycle_stakeholder on (stakeholder_id)

#### Report (table: analytics.reports)

| Field | Type | Column | Constraints |
|---|---|---|---|
| id | UUID | id | PK, auto-generated |
| tenantId | UUID | tenant_id | NOT NULL |
| reportType | String(50) | report_type | NOT NULL |
| periodStart | LocalDate | period_start | NOT NULL |
| periodEnd | LocalDate | period_end | NOT NULL |
| data | Map<String,Object> | data (JSONB) | NOT NULL |
| generatedAt | Instant | generated_at | defaults to now |

**Index:** idx_reports_tenant_type on (tenant_id, report_type, period_start, period_end)

### 4.2 DTO Records (Response Models)

- **DashboardData** -- Composite dashboard with summary cards, health status, urgent decisions, velocity trends, stakeholder leaderboard, pipeline status
- **DataPoint(date, value)** -- Chart data point
- **DecisionSummary(id, title, priority, ownerName, waitingHours, blockedItemsCount)** -- Urgent decision
- **StakeholderScore(stakeholderId, name, avgResponseTimeHours, decisionsCompleted, slaComplianceRate, rank)** -- Leaderboard entry
- **DecisionVelocityMetric** -- Per-day decision velocity with breakdowns
- **OutcomeVelocityMetric** -- Per-day outcome validation counts
- **StakeholderResponseMetric** -- Per-stakeholder response time with period
- **HypothesisThroughputMetric** -- Per-day hypothesis counts with validation rate
- **WeeklyDigestReport** -- Full weekly digest with trends, highlights, concerns
- **OutcomeReport** -- Outcome-specific report with decisions, hypotheses, key results, timeline
- **TimelineEvent(timestamp, eventType, description)** -- Report timeline entry
- **KeyResultProgress(title, targetValue, currentValue, unit, progressPercent)** -- Defined but always empty
- **Insight(type, title, description, recommendation, confidence, generatedAt)** -- AI insight
- **Trend(metricName, direction, percentChange, periodDays, isSignificant)** -- Detected trend

### 4.3 Kafka Event Records (Inbound)

- **DecisionResolvedEvent** -- tenantId, projectId, decisionId, title, priority, decisionType, resolvedBy, stakeholderId, wasEscalated, createdAt, resolvedAt
- **OutcomeValidatedEvent** -- tenantId, projectId, outcomeId, title, validatedBy, createdAt, validatedAt
- **OutcomeInvalidatedEvent** -- tenantId, projectId, outcomeId, title, invalidatedBy, createdAt, invalidatedAt
- **HypothesisConcludedEvent** -- tenantId, projectId, hypothesisId, outcomeId, result, createdAt, concludedAt

### 4.4 Internal Request Records

- **DecisionResolvedRequest** -- mirrors DecisionResolvedEvent fields
- **OutcomeValidatedRequest** -- mirrors OutcomeValidatedEvent fields
- **HypothesisConcludedRequest** -- mirrors HypothesisConcludedEvent fields

### 4.5 Core Service Client DTOs

- **CorePageResponse(totalElements, totalPages, size, number)** -- Paginated wrapper
- **CoreListResponse<T>(content, totalElements)** -- List wrapper
- **CoreDecisionSummary** -- id, title, priority, status, ownerId, ownerName, createdAt, blockedItemsCount
- **CoreStakeholderInfo** -- id, name, email, role, decisionsPending, decisionsCompleted
- **CoreOutcomeInfo** -- id, title, status, successCriteria, createdAt, validatedAt, ownerId

---

## 5. API Endpoints

All endpoints are prefixed with `/api/v1`. Tenant isolation is achieved via the `X-Tenant-Id` request header.

### 5.1 Dashboard Endpoints

| Method | Path | Description | Auth | Parameters |
|---|---|---|---|---|
| GET | /api/v1/dashboard | Full dashboard data | None (header: X-Tenant-Id) | projectId (optional query param) |
| GET | /api/v1/dashboard/summary | Quick summary (mobile-friendly) | None (header: X-Tenant-Id) | projectId (optional query param) |

### 5.2 Metrics Endpoints

| Method | Path | Description | Auth | Parameters |
|---|---|---|---|---|
| GET | /api/v1/metrics/decision-velocity | Decision cycle time trend | None (header: X-Tenant-Id) | projectId (optional), days (default: 30) |
| GET | /api/v1/metrics/stakeholder-response | Response times by stakeholder | None (header: X-Tenant-Id) | days (default: 30) |
| GET | /api/v1/metrics/outcome-velocity | Outcomes validated/invalidated | None (header: X-Tenant-Id) | projectId (optional), days (default: 30) |
| GET | /api/v1/metrics/hypothesis-throughput | Experiments per period | None (header: X-Tenant-Id) | projectId (optional), days (default: 30) |
| GET | /api/v1/metrics/pipeline-idle-time | Pipeline idle time status | None (header: X-Tenant-Id) | None |

### 5.3 Reports Endpoints

| Method | Path | Description | Auth | Parameters |
|---|---|---|---|---|
| GET | /api/v1/reports | List available reports and recent reports | None (header: X-Tenant-Id) | None |
| GET | /api/v1/reports/weekly-digest | Weekly digest report | None (header: X-Tenant-Id) | weekStart (optional, ISO date) |
| GET | /api/v1/reports/outcome/{outcomeId} | Outcome-specific report | None (header: X-Tenant-Id) | outcomeId (path) |

### 5.4 Insights Endpoints

| Method | Path | Description | Auth | Parameters |
|---|---|---|---|---|
| GET | /api/v1/insights | AI-generated insights | None (header: X-Tenant-Id) | projectId (optional) |
| GET | /api/v1/insights/trends | Detected trends | None (header: X-Tenant-Id) | projectId (optional) |
| GET | /api/v1/insights/recommendations | Actionable recommendations | None (header: X-Tenant-Id) | projectId (optional) |

### 5.5 Internal Endpoints (service-to-service)

| Method | Path | Description | Auth | Body |
|---|---|---|---|---|
| POST | /api/v1/internal/metrics/decision-resolved | Record decision resolved | None | DecisionResolvedRequest JSON |
| POST | /api/v1/internal/metrics/outcome-validated | Record outcome validated | None | OutcomeValidatedRequest JSON |
| POST | /api/v1/internal/metrics/hypothesis-concluded | Record hypothesis concluded | None | HypothesisConcludedRequest JSON |

### 5.6 Actuator Endpoints

| Method | Path | Description |
|---|---|---|
| GET | /actuator/health | Health check (includes Kafka health indicator) |
| GET | /actuator/info | Application info |
| GET | /actuator/metrics | Micrometer metrics |

---

## 6. Security

### 6.1 Authentication

**There is NO authentication.** The service has no Spring Security dependency, no security configuration, and no token validation. All endpoints are completely open.

### 6.2 Tenant Isolation

Tenant context is provided via the `X-Tenant-Id` HTTP header. Controllers read this header and pass it to service methods, which use it in all database queries.

**Critical security gap:** There is no validation that the caller is authorized for the tenant they claim. Any client can access any tenant's data by setting an arbitrary `X-Tenant-Id` header.

### 6.3 Internal Endpoints

The `/api/v1/internal/metrics/*` POST endpoints are intended for service-to-service communication but have no access restrictions. Any HTTP client can push arbitrary metric data.

### 6.4 Core Service Communication

Communication with Zevaro-Core uses RestTemplate with the `X-Tenant-Id` header. There is no service-to-service authentication (no API keys, no mTLS, no JWT propagation).

---

## 7. Database

### 7.1 Schema

- **Database:** PostgreSQL 16 (shared `zevaro` database)
- **Schema:** `analytics` (configured via `hibernate.default_schema`)
- **Init script:** `init-schema.sql` creates `analytics`, `core`, and `integrations` schemas

### 7.2 Tables (Hibernate-managed)

| Table | Entity | Purpose |
|---|---|---|
| analytics.metric_snapshots | MetricSnapshot | Time-series metric data (daily granularity) |
| analytics.decision_cycle_log | DecisionCycleLog | Individual decision cycle time records |
| analytics.reports | Report | Persisted generated reports (JSONB data) |

### 7.3 Migration Approach

- **DDL strategy:** `hibernate.ddl-auto: update` -- Hibernate auto-creates/alters tables on startup.
- **No migration tool:** No Flyway, Liquibase, or versioned SQL migrations.
- The CONVENTIONS.md describes tables with SQL DDL including a `metric_snapshots` unique constraint on `(tenant_id, metric_type, metric_date, dimensions)`, but the actual JPA entity defines the unique constraint on `(tenant_id, metric_type, metric_date)` only (without `dimensions`). This is a mismatch.

### 7.4 JSONB Usage

- `MetricSnapshot.dimensions` -- Stores dimensional breakdown data (e.g., `{"invalidated": 2, "validated": 3}`)
- `Report.data` -- Stores the full report payload as JSONB

### 7.5 Indexes

All indexes are defined via JPA annotations:
- `idx_metric_tenant_type_date` on metric_snapshots(tenant_id, metric_type, metric_date)
- `idx_cycle_tenant_resolved` on decision_cycle_log(tenant_id, resolved_at)
- `idx_cycle_stakeholder` on decision_cycle_log(stakeholder_id)
- `idx_reports_tenant_type` on reports(tenant_id, report_type, period_start, period_end)

---

## 8. Event System (Kafka)

### 8.1 Consumed Topics

| Topic | Consumer | Action |
|---|---|---|
| zevaro.core.decision.resolved | DecisionEventConsumer | Records decision cycle log + updates daily DECISION_VELOCITY snapshot |
| zevaro.core.outcome.validated | OutcomeEventConsumer | Increments daily OUTCOME_VELOCITY snapshot |
| zevaro.core.outcome.invalidated | OutcomeInvalidatedEventConsumer | Increments invalidated count in OUTCOME_VELOCITY dimensions |
| zevaro.core.hypothesis.concluded | HypothesisEventConsumer | Creates/increments HYPOTHESIS_THROUGHPUT snapshot with validated/invalidated dimensions |

### 8.2 Produced Topics

**None.** This service is a pure consumer. It does not produce any Kafka events.

### 8.3 Consumer Configuration

- **Consumer group:** zevaro-analytics
- **Auto offset reset:** earliest
- **Deserializer:** JsonDeserializer with trusted packages `ai.zevaro.*`
- **Concurrency:** 1 thread per listener (reduced from 3 due to the 278GB log incident)
- **Conditional on:** `spring.kafka.enabled` property (defaults to true)

### 8.4 Error Handling

- **FixedBackOff:** 3 retries with 5-second intervals, then message is dropped
- **Non-retryable exceptions:** DeserializationException, JsonParseException, JsonMappingException
- **Consumer-level:** Each consumer has try-catch with:
  - DataIntegrityViolationException -- silently ignored (duplicate events)
  - DataAccessException -- re-thrown for retry
  - Other Exception -- wrapped in RuntimeException and re-thrown
- **Rate-limited logging:** RateLimitedConsumerLogger logs one message per 5-minute window per error category

### 8.5 Defensive Settings

- reconnect.backoff.ms: 1000 (default 50ms)
- reconnect.backoff.max.ms: 60000
- session.timeout.ms: 30000
- heartbeat.interval.ms: 10000
- request.timeout.ms: 40000
- retry.backoff.ms: 1000

### 8.6 Health Monitoring

KafkaHealthIndicator provides:
- Actuator health endpoint integration
- Rate-limited failure logging (first failure immediately, then every 5 minutes)
- Recovery detection and logging with downtime summary
- Consecutive failure tracking

---

## 9. Configuration

### 9.1 Application Properties (application.yml)

```yaml
server.port: 8081
spring.application.name: zevaro-analytics

# Database
spring.datasource.url: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:zevaro}
spring.datasource.username: ${DB_USER:zevaro}
spring.datasource.password: ${DB_PASSWORD:zevaro}
spring.jpa.hibernate.ddl-auto: update
spring.jpa.properties.hibernate.default_schema: analytics
spring.jpa.open-in-view: false

# Redis
spring.redis.host: ${REDIS_HOST:localhost}
spring.redis.port: ${REDIS_PORT:6379}

# Kafka
spring.kafka.enabled: ${KAFKA_ENABLED:true}
spring.kafka.bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
spring.kafka.consumer.group-id: zevaro-analytics
spring.kafka.consumer.auto-offset-reset: earliest
spring.kafka.listener.auto-startup: ${KAFKA_AUTO_STARTUP:true}

# Actuator
management.endpoints.web.exposure.include: health,info,metrics
management.endpoint.health.show-details: when_authorized

# Core service URL
services.core.url: ${CORE_SERVICE_URL:http://localhost:8080}
```

### 9.2 Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| DB_HOST | localhost | PostgreSQL host |
| DB_NAME | zevaro | Database name |
| DB_USER | zevaro | Database username |
| DB_PASSWORD | zevaro | Database password |
| REDIS_HOST | localhost | Redis host |
| REDIS_PORT | 6379 | Redis port |
| KAFKA_ENABLED | true | Toggle Kafka consumers on/off |
| KAFKA_SERVERS | localhost:9092 | Kafka bootstrap servers |
| KAFKA_AUTO_STARTUP | true | Auto-start Kafka listeners |
| SHOW_SQL | false | Show Hibernate SQL |
| CORE_SERVICE_URL | http://localhost:8080 | Zevaro-Core base URL |

### 9.3 Caching Strategy (Redis)

| Cache Name | TTL | Purpose |
|---|---|---|
| dashboard | 1 minute | Dashboard data (frequently accessed, needs freshness) |
| metrics | 5 minutes | Metrics queries and insights |
| reports | 1 hour | Generated reports (expensive computation) |

Serialization: GenericJackson2JsonRedisSerializer

### 9.4 Profiles

No profile-specific configuration files exist (e.g., no application-dev.yml or application-prod.yml), despite the CONVENTIONS.md referencing application-dev.yml.

### 9.5 Docker

- **Dockerfile:** Multi-stage build (Maven 3.9 + Temurin 21 Alpine for build, Temurin 21 JRE Alpine for runtime). Runs as non-root `zevaro` user.
- **docker-compose.yml:** PostgreSQL 16 Alpine, Redis 7 Alpine, Zookeeper (Confluent 7.5.0), Kafka (Confluent 7.5.0). All with health checks.

---

## 10. Code Quality Observations

### 10.1 Strengths

- **Defensive Kafka configuration.** The 278GB log incident clearly informed thorough defensive settings: backoff tuning, reduced concurrency, rate-limited logging, conditional Kafka enablement, and a custom health indicator. This is well-documented and well-implemented.
- **Rate-limited logging pattern.** RateLimitedConsumerLogger and the rate-limited logging in CoreServiceClient and KafkaHealthIndicator prevent log storms during outages.
- **Consistent consumer error handling.** All four Kafka consumers follow the same error handling pattern (DataIntegrityViolation caught silently, DataAccessException re-thrown, general Exception wrapped).
- **Cache eviction on writes.** MetricsService evicts dashboard cache when new events are recorded, ensuring dashboard freshness.
- **Immutable DTOs.** All DTOs are Java records, providing immutability and value semantics.
- **Graceful degradation.** CoreServiceClient returns empty/null/zero defaults when Core is unreachable rather than failing the request.
- **Good test coverage intent.** The project has 9 test files covering service logic, controllers, and consumer error handling.

### 10.2 Issues and Concerns

#### CRITICAL

1. **No authentication or authorization.** All endpoints are completely open. Any HTTP client can read any tenant's analytics data or push arbitrary metrics via the internal endpoints. This is a significant security vulnerability for a multi-tenant system.

2. **No input validation on internal endpoints.** InternalMetricsController accepts POST requests with no @Valid annotations and no validation on any fields. An attacker could push arbitrary data (null tenantIds, negative cycle times, future dates, etc.).

3. **`hibernate.ddl-auto: update` in all environments.** There is no environment-specific override for production. Using `update` in production is risky -- Hibernate can add columns/tables but never removes them, and schema changes may not be reversible.

#### HIGH

4. **@EnableKafka is declared in two places.** Both ZevaroAnalyticsApplication.java and KafkaConsumerConfig.java have @EnableKafka. When KAFKA_ENABLED=false, KafkaConsumerConfig is not loaded, but @EnableKafka on the main application class still enables the Kafka auto-configuration infrastructure, which may cause connection attempts. This undermines the conditional toggle.

5. **@EnableCaching is declared in two places.** Both ZevaroAnalyticsApplication.java and CacheConfig.java have @EnableCaching. This is redundant but not harmful.

6. **MetricSnapshot unique constraint excludes projectId.** The unique constraint is (tenant_id, metric_type, metric_date) but the service now has project-level filtering (projectId). If two different projects record the same metric type on the same date for the same tenant, the second will fail with a constraint violation or silently overwrite the first (depending on whether findByTenantIdAndMetricTypeAndMetricDate is used, which ignores projectId).

7. **Race condition in MetricsService snapshot upserts.** The recordOutcomeValidated, recordOutcomeInvalidated, and recordHypothesisConcluded methods do a read-then-write without pessimistic locking. Under concurrent Kafka event processing, two threads could read the same snapshot, both increment by 1, and one increment would be lost. The @Transactional annotation does not prevent this without SERIALIZABLE isolation or explicit row locking.

8. **Escalation rate in MetricsController.getStakeholderResponse() is hardcoded, not computed.** The escalation rate is determined by arbitrary thresholds on response time (> 48h -> 0.3, > 24h -> 0.15, else -> 0.05). This is not the actual escalation rate from data.

#### MEDIUM

9. **No @Validated/@Valid on request parameters.** The `days` parameter on metrics endpoints has no bounds checking. A caller could pass days=999999 or days=-1.

10. **Missing logback-spring.xml.** The CONVENTIONS.md mandates a separate Kafka log appender with strict size limits, but no logback configuration file exists in src/main/resources/. The service relies on Spring Boot defaults, which means Kafka log flooding protection described in conventions is not implemented at the log framework level.

11. **DataPoint record inconsistency with tests.** The actual DataPoint record is DataPoint(LocalDate date, double value) but test files DashboardControllerTest and ReportControllerTest construct it as new DataPoint("2024-01-01", 5) (String, int). These tests would not compile against the current DataPoint record.

12. **DashboardServiceTest references methods/fields that do not exist.** The test calls dashboardService.getDashboard(TEST_TENANT_ID) with one argument, but the actual DashboardService.getDashboard() takes two arguments (tenantId, projectId). The test also accesses fields like dashboard.pendingDecisionCount(), dashboard.avgCycleTimeHours(), dashboard.outcomesThisWeek(), dashboard.activeExperiments(), dashboard.healthStatus(), dashboard.leaderboard() -- but the actual DashboardData record uses different names: decisionsPendingCount, avgDecisionWaitHours, outcomesValidatedThisWeek, experimentsRunning, decisionHealthStatus, stakeholderLeaderboard.

13. **InsightsControllerTest constructs Insight and Trend with wrong field signatures.** The test creates Insight with (UUID, InsightType, String, String, double, Instant) where the first field is a UUID id, but the actual Insight record is (InsightType, String, String, String, double, Instant). Similarly, InsightType values OPPORTUNITY and WARNING used in the test do not exist in the actual InsightType enum (only TREND, BOTTLENECK, ANOMALY, RECOMMENDATION, ACHIEVEMENT). The test Trend constructor uses (String, TrendDirection, double, String) but the actual is (String, TrendDirection, double, int, boolean).

14. **MetricsServiceTest calls recordDecisionResolved with wrong number of arguments.** The test calls it with 8 arguments (missing projectId), but the actual method requires 9 arguments. Similarly, recordOutcomeValidated is called with 4 arguments but the actual method requires 5 (missing projectId). Same for recordHypothesisConcluded and recordOutcomeInvalidated.

15. **OutcomeInvalidatedEventConsumerTest constructs OutcomeInvalidatedEvent with wrong arguments.** The test passes 6 arguments without projectId, but the actual record has 7 fields including projectId.

16. **DashboardServiceTest references getDashboardSummary(UUID) with one argument, but the actual method takes (UUID, UUID) (tenantId, projectId).**

17. **ReportServiceTest accesses wrong field names.** It references report.decisionsResolvedCount(), report.outcomesValidatedCount(), etc., but the actual WeeklyDigestReport uses decisionsResolved, outcomesValidated, etc. Also references report.changePercentFromPreviousWeek() but the actual field is cycleTimeChangePercent. Also references report.title() on OutcomeReport but the actual field is outcomeTitle(). Also references report.totalDecisionsResolved(), report.totalHypothesesTested() but the actual fields are totalDecisions, decisionsResolved, totalHypotheses.

18. **OutcomeReport has a suspicious duplicate field.** The constructor in ReportService passes totalDecisions for both totalDecisions and decisionsResolved parameters: `totalDecisions, totalDecisions, avgDecisionTime`. The second should likely be a filtered count, but it is just the same value.

19. **InsightsService.getRecommendations() does not use projectId for the escalation rate query.** The cycleLogRepository.findByTenantIdAndResolvedAtBetween() call ignores projectId, so recommendations are always tenant-wide even when a project filter is provided.

20. **@Cacheable on controller methods.** MetricsController has @Cacheable directly on REST controller methods. While this works for controller methods (called from outside via proxy), it mixes caching concerns into the controller layer rather than the service layer.

21. **Pipeline idle time is a stub.** MetricsController.getPipelineIdleTime() returns hardcoded idleTimeMinutes: 0 and lastDecisionResolved: Instant.now() with a comment "Requires Elaro integration (ZI-009)".

22. **KeyResultProgress DTO is defined but never populated.** OutcomeReport.keyResults is always List.of() with a comment noting it requires Core service integration.

#### LOW

23. **DashboardData.pipelineStatus, lastDeployment, idleTimeMinutes are always hardcoded.** The DashboardService returns "IDLE", null, and 0 respectively with TODO comments about Elaro integration.

24. **calculateHealthStatus in DashboardService ignores escalatedCount parameter.** The method accepts it but only uses avgCycleTime to determine health status.

25. **SLA compliance rate in buildLeaderboard() is heuristic, not data-driven.** It uses arbitrary response time thresholds (< 24h -> 1.0, < 48h -> 0.75, else -> 0.5) rather than actual SLA definitions.

26. **@EnableScheduling on application class but no scheduled tasks.** No @Scheduled methods exist anywhere in the codebase.

27. **LICENSE file references "Zevaro-Core Licensing" in its header** despite being in the Zevaro-Analytics repository.

28. **.gitignore only contains `target/`.** IDE files, .env, *.log, .DS_Store, and other common exclusions are missing. (.DS_Store files are already present in the repo.)

---

## 11. Known Issues

### 11.1 Tests Will Not Compile

The test files appear to have been written against a different (likely earlier) version of the DTOs and service signatures. Based on the analysis in section 10.2 items 11-17, the following test files have compilation errors:

- **DashboardControllerTest** -- wrong DataPoint constructor, wrong DashboardService.getDashboard method signature (1 arg vs 2), wrong DashboardService.getDashboardSummary method signature
- **DashboardServiceTest** -- wrong method signatures, wrong field accessor names on DashboardData
- **MetricsServiceTest** -- wrong argument count on recordDecisionResolved (8 vs 9), recordOutcomeValidated (4 vs 5), recordHypothesisConcluded (6 vs 7), recordOutcomeInvalidated (4 vs 5)
- **InsightsControllerTest** -- wrong Insight constructor, non-existent InsightType values, wrong Trend constructor
- **InsightsServiceTest** -- wrong InsightsService.generateInsights() signature (1 arg vs 2), wrong InsightsService.detectTrends() signature (1 arg vs 2), wrong InsightsService.getRecommendations() signature (1 arg vs 2)
- **ReportControllerTest** -- wrong DataPoint constructor
- **ReportServiceTest** -- wrong field accessor names on WeeklyDigestReport and OutcomeReport
- **OutcomeInvalidatedEventConsumerTest** -- wrong OutcomeInvalidatedEvent constructor (6 args vs 7, missing projectId), wrong MetricsService.recordOutcomeInvalidated arg count (4 vs 5, missing projectId)

### 11.2 Missing Features (documented as TODOs in code)

1. **Elaro integration (ZI-009)** -- Pipeline status, last deployment, idle time are all stubbed
2. **Key results in outcome reports** -- Always empty (List.of())
3. **Per-priority and per-type breakdowns in decision velocity** -- Always Map.of() in MetricsController
4. **Stakeholder response metric endpoint does not support projectId filter** -- only tenant-wide
5. **Elasticsearch integration** -- Referenced in CONVENTIONS.md but not implemented
6. **Logback configuration file** -- Referenced in CONVENTIONS.md as mandatory but not present
7. **POST /api/v1/reports/generate endpoint** -- Listed in CONVENTIONS.md but not implemented

### 11.3 CONVENTIONS.md Drift

The CONVENTIONS.md describes the intended architecture but has drifted from the actual implementation:

| CONVENTIONS.md Says | Reality |
|---|---|
| ElasticsearchConfig.java exists | Not present, no Elasticsearch dependency |
| MetricsCalculator.java class | Service is called MetricsService.java |
| ReportGenerator.java class | Service is called ReportService.java |
| InsightsEngine.java class | Service is called InsightsService.java |
| TrendDetector.java, RecommendationService.java | Consolidated into InsightsService.java |
| MetricSnapshotRepository.java only | Additional DecisionCycleLogRepository and ReportRepository exist |
| Unique constraint includes dimensions | Unique constraint does not include dimensions |
| application-dev.yml exists | Does not exist |
| Logback XML configuration mandatory | No logback configuration file exists |
| POST /api/v1/reports/generate endpoint | Not implemented |
| GET /api/v1/metrics/pipeline-idle-time functional | Returns hardcoded stub data |

### 11.4 MetricSnapshot Unique Constraint vs ProjectId

The MetricSnapshot entity has a unique constraint on (tenant_id, metric_type, metric_date) but the service now tracks projectId. The metric recording methods (e.g., recordOutcomeValidated) use findByTenantIdAndMetricTypeAndMetricDate which ignores projectId. This means:
- Metrics from different projects within the same tenant on the same day will collide
- The first project to record wins; subsequent projects will increment the same snapshot
- Project-level metric filtering will return incorrect data

### 11.5 @MockBean Package Path

The test files use org.springframework.boot.test.mock.MockBean which is the old package path. In Spring Boot 3.4+, this was relocated. Since the project uses Spring Boot 3.3.0, this is currently fine but will be an issue on upgrade.

---

## 12. File Inventory

### Source Files (59 total: 39 main + 9 test + 11 config/resource)

**Main Java files:** 39
**Test Java files:** 9
**Configuration:** application.yml, pom.xml, Dockerfile, docker-compose.yml, init-schema.sql
**Documentation:** README.md, Zevaro-Analytics-CONVENTIONS.md, LICENSE
**Build:** mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
