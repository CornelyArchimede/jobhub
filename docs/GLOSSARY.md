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

## Technologies to learn

What's coming, roughly in milestone order. ✅ once you've used it hands-on.

| Technology / concept | First milestone | Notes |
|----------------------|-----------------|-------|
| Spring Boot 4 / Spring Framework 7 | M0 | |
| Flyway migrations | M0 | |
| Testcontainers | M0 | |
| Spring `RestClient` | M1 | Synchronous HTTP client; timeouts + retry |
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
