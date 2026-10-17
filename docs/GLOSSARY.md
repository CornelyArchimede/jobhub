# Glossary

Rolling list of terms and technologies that come up while building Job Hub. Added to every milestone.

## Terms

| Term | Meaning | Where it shows up |
|------|---------|-------------------|
| Flyway | Runs versioned SQL files (`V1__x.sql`, `V2__y.sql`) in order once each, recording them in `flyway_schema_history`. | `src/main/resources/db/migration/` |
| `ddl-auto=validate` | Hibernate checks entity mappings against the existing schema at startup and fails on mismatch; it never creates or alters tables. | `application.yml` |
| Testcontainers | Library that starts real Docker containers (here Postgres 17) for the duration of a test run. | `TestcontainersConfiguration` |
| `@ServiceConnection` | Spring Boot annotation that reads a container's host/port/credentials and wires the DataSource to it automatically, no URL properties needed. | `TestcontainersConfiguration` |
| Docker Compose support | `spring-boot-docker-compose` runs `docker compose up` on `compose.yaml` at startup and auto-configures the connection to those services. | `compose.yaml`, `pom.xml` |
| Actuator | Production endpoints (`/actuator/health`, metrics, info) exposed by Spring Boot. Health aggregates checks like DB connectivity. | `application.yml` |
| springdoc-openapi | Generates an OpenAPI spec from your controllers and serves Swagger UI to try endpoints in a browser. | `/swagger-ui.html` |
| Open Session in View (OSIV) | Spring default that keeps the JPA session open during view rendering so lazy loads "just work"; hides N+1 queries. Turned off. | `application.yml` |
| Maven wrapper (`mvnw`) | Script that downloads a pinned Maven version, so everyone builds with the same Maven without installing it. | `mvnw`, `.mvn/wrapper/` |
| Modular starters (Boot 4) | Boot 4 split auto-config into per-technology modules, e.g. `spring-boot-starter-flyway`, `-webmvc`, each with a matching `-test` starter. | `pom.xml` |
| ATS (Applicant Tracking System) | Software companies use to post jobs and manage applicants (Greenhouse, Lever, Ashby, Workday). Each has its own public job-board API. | `ingest/`, `AtsType` |
| Board token | A company's ID on an ATS: the path segment in `boards.greenhouse.io/<token>`. Every board API call needs it; there's no global list of them. | `/dev/preview/{boardToken}`, M3 watchlist |
| Sensitivity threshold | The minimum score a job needs to show up in results. Lower = looser matches. | M4 scoring / M5 read API |
| Curated list vs crawl | Ingesting a known list of sources vs discovering sources automatically. Trade completeness for cost, politeness and predictability. | DESIGN.md "Vision and scale path" |
| Spring Initializr | start.spring.io: generates a Boot project skeleton (pom, wrapper, main class) for chosen versions and dependencies. | How M0 was scaffolded |
| Container vs image | An image is the read-only template (`postgres:17`); a container is a running instance of it. Many containers can come from one image. | `compose.yaml`, Testcontainers |
| Named volume | Docker-managed storage that outlives the container, so data survives restarts and `docker compose down` (but not `down -v`). | `pgdata` in `compose.yaml` |
| Compose `start-only` lifecycle | Boot starts the Compose services if they aren't running but never stops them on shutdown, so Postgres stays up between app runs. | `spring.docker.compose.lifecycle-management` |
| WSL2 | Windows Subsystem for Linux: a real Linux kernel in a lightweight VM. Docker Desktop on Windows runs its engine inside it. | Docker prerequisite |
| Health indicator | One check that contributes to `/actuator/health` (e.g. `db` runs a validation query). Overall status is the worst of all indicators. | `/actuator/health` |
| `RestClient` | Spring's synchronous, fluent HTTP client (successor to `RestTemplate`): `restClient.get().uri(...).retrieve().body(T.class)`. | `GreenhouseConfig`, `GreenhouseClient` |
| `MockRestServiceServer` | Test double that plugs into a `RestClient.Builder`, checks each outgoing request against expectations, and returns canned responses. No real network. | `GreenhouseClientTest` |
| `ExpectedCount` | How many times `MockRestServiceServer` expects a request: `once()`, `between(1, 2)`, `never()`. Used to pin retry behavior. | `GreenhouseClientTest` |
| `@ConfigurationProperties` | Binds a prefix of `application.yml` (e.g. `jobhub.greenhouse.*`) to a typed record, so config is validated and type-safe instead of scattered `@Value`s. | `GreenhouseProperties` |
| `@WebMvcTest` | Test slice that starts only the web layer (controllers, advice, JSON) with no DB; collaborators are replaced with `@MockitoBean`. | `DevPreviewControllerTest` |
| `MockMvcTester` | AssertJ-style API for `MockMvc`: `assertThat(mvc.get().uri(...)).hasStatusOk().bodyJson()...`. | `DevPreviewControllerTest` |
| ProblemDetail (RFC 9457) | Standard JSON error body (`type`, `title`, `status`, `detail` + extras) served as `application/problem+json`. | `ApiExceptionHandler` |
| HTML entities | Escaped characters like `&lt;` for `<` and `&amp;` for `&`. Greenhouse escapes its whole HTML description, so it has to be unescaped *then* stripped. | `GreenhouseClient.htmlToText` |
| jsoup | Java HTML parser; `Jsoup.parse(html).text()` unescapes entities and drops tags. | `GreenhouseClient.htmlToText` |
| N+1 calls | One call to list N items, then one more call per item. Cost grows with N; the classic fix is batching, the fallback is bounded concurrency. | Pay fetch |
| Bounded concurrency / `Semaphore` | Run work in parallel but cap how many are in flight; a `Semaphore(n)` hands out n permits. Protects the remote API and you. | Pay fan-out |
| Virtual threads | Lightweight JVM threads (Java 21+): cheap to create by the thousand, ideal for blocking I/O like HTTP calls. `Executors.newVirtualThreadPerTaskExecutor()`. | Pay fan-out |
| `@Retryable` (Spring 7) | Built-in retry annotation in Spring Framework 7 (`org.springframework.resilience`), enabled with `@EnableResilientMethods`. One option for "one retry max". | `GreenhouseClient` (your choice) |
| `--force-with-lease` | A force push that only goes through if the remote branch is still at the commit you expect, so you can't clobber someone else's push. | Rewriting the M0 commit |

## Technologies to learn

What's coming, roughly in milestone order. ✅ once you've used it hands-on.

| Technology / concept | First milestone | Notes |
|----------------------|-----------------|-------|
| Spring Boot 4 / Spring Framework 7 | M0 ✅ | |
| Flyway migrations | M0 ✅ | |
| Testcontainers | M0 ✅ | |
| Docker / Compose basics | M0 ✅ | Images, containers, volumes, ports |
| Spring `RestClient` | M1 | Synchronous HTTP client; timeouts + retry |
| `MockRestServiceServer`, `@WebMvcTest` | M1 | Testing HTTP clients and controllers without a network or DB |
| Virtual threads + `Semaphore` | M1 | Bounded-concurrency pay fan-out |
| jsoup | M1 | HTML → plain text |
| Greenhouse Job Board API | M1 | Public, unauthenticated JSON |
| Postgres `INSERT ... ON CONFLICT` (upsert) | M2 | Idempotent ingest |
| Content hashing for change detection | M2 | `content_hash` decides rescoring |
| `JdbcClient` | M2 | Native SQL alongside JPA |
| `@Scheduled` / scheduling | M3 | |
| Lever Postings API | M3 | |
| `@ConfigurationProperties` + records | M4 | Rubric in `application.yml` |
| `jsonb` columns | M4 | Score breakdown |
| Keyset (seek) pagination | M5 | vs OFFSET |
| Composite indexes, `EXPLAIN ANALYZE` | M5 | |
| State machine / validated transitions | M6 | |
| Spring Cache + Caffeine, eviction, hit rate | M7 | |
