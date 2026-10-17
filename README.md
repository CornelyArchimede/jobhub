# Job Hub API

Personal job-search backend. Pulls postings from public ATS job boards (Greenhouse, Lever, Ashby) for a watchlist of companies, scores each one against a personal rubric, and tracks applications through a pipeline.

Single user, backend only. Swagger UI is the v1 "frontend".

It's also a deliberate exercise in backend fundamentals: schema design, idempotent ingest, indexing, keyset pagination and caching. Each non-obvious choice is written up in [docs/DESIGN.md](docs/DESIGN.md).

## Stack

Java 25 · Spring Boot 4 · Maven · PostgreSQL 17 · Flyway · Spring Data JPA + `JdbcClient` · Spring `RestClient` · Caffeine · springdoc-openapi · Actuator · JUnit 5 + Testcontainers

## Running locally

**Prerequisites:** JDK 25, and Docker running (Docker Desktop on Windows/macOS).

```bash
./mvnw spring-boot:run
```

Spring Boot's Docker Compose support starts Postgres 17 from [`compose.yaml`](compose.yaml), Flyway applies the migrations, and the app starts on port 8080.

| What | URL |
|------|-----|
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |

## Tests

```bash
./mvnw test
```

Tests run against a real, throwaway Postgres 17 container via Testcontainers (no H2), so Docker must be running.

## Project layout

Packaged by feature under `dev.archi.jobhub`:

```
company/      watchlist of companies and their ATS board tokens
ingest/       ATS clients (Greenhouse, Lever, Ashby), ingest service, scheduler
job/          job postings, search/read API
scoring/      rubric + pure scoring function
application/  application pipeline, status transitions, history
common/       errors, shared config
```

Schema lives in hand-written Flyway migrations under `src/main/resources/db/migration/`. Hibernate only validates the schema; it never generates it.

## Roadmap

- [x] **M0** Scaffold: app boots, Postgres via Compose, Flyway, health, Swagger UI
- [ ] **M1** Greenhouse client → preview normalized jobs for one board
- [ ] **M2** Persist postings with an idempotent upsert
- [ ] **M3** Company watchlist, scheduled ingest, closed-posting detection, Lever client
- [ ] **M4** Rubric-based scoring with a JSON score breakdown
- [ ] **M5** Read API: filters, keyset pagination, indexes (with `EXPLAIN ANALYZE`)
- [ ] **M6** Application pipeline with validated status transitions
- [ ] **M7** Caffeine caching on hot queries
- [ ] **M8** Design write-up

## Docs

- [docs/DESIGN.md](docs/DESIGN.md): decisions and the reasoning behind them
- [docs/GLOSSARY.md](docs/GLOSSARY.md): terms and technologies used in the project
