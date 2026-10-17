# Job Hub API: Design Notes

Why things are the way they are. One line of "why" per non-obvious choice; expanded into interview talking points in M8.

## Decisions

| # | Decision | Why | Milestone |
|---|----------|-----|-----------|
| 1 | Flyway + `ddl-auto=validate` | Schema is versioned, reviewed SQL; Hibernate only checks entities match it, never mutates prod schema. | M0 |
| 2 | Testcontainers Postgres, no H2 | Tests run against the same engine/version as prod, so `ON CONFLICT`, `jsonb`, and planner behavior are real. | M0 |
| 3 | Caffeine, not Redis | Single instance; in-process cache has zero network hop and no extra infra. Revisit if multi-instance. | M0 |
| 4 | `open-in-view: false` | No lazy loading during JSON rendering; forces queries to happen in the service layer where they're visible. | M0 |
| 5 | Compose `start-only` lifecycle | Postgres stays up between app restarts, so restarts are fast and data persists in a named volume. | M0 |

## Schema

_TBD (M2)._

## Ingest & idempotency

_TBD (M2–M3)._

## Scoring

_TBD (M4)._

## Read API, pagination & indexes

_TBD (M5). Include `EXPLAIN ANALYZE` before/after._

## Caching

_TBD (M7)._
