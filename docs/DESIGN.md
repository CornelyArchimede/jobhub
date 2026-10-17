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
| 6 | Postgres pinned to `postgres:17` in both Compose and Testcontainers | Dev, tests and (eventual) prod run the same major version; `latest` would silently drift. | M0 |
| 7 | V1 migration is an empty baseline | Proves the Flyway pipeline end to end before any schema exists; real tables start at V2 so history stays append-only. | M0 |
| 8 | Health shows details (`show-details: always`) | Single-user local app; seeing the `db` component is more useful than hiding it. Would be `when-authorized` if exposed publicly. | M0 |
| 9 | Greenhouse: one list call with `content=true` | Gets every field except pay in a single request (~5.7 MB for Stripe's 732 jobs) instead of 1 + N calls. | M1 |
| 10 | Pay via per-job detail calls, bounded concurrency, behind `jobhub.greenhouse.fetch-pay` | Pay only exists on the detail endpoint (N+1). Cap in-flight calls (default 8) to be polite and fast (~50 s sequential vs ~7 s); toggle off for boards like Stripe that publish no ranges. | M1 |
| 11 | A failed detail call → null pay, never a failed board | Pay is optional enrichment; CLAUDE.md's "log and skip" applied per job. A 404 on the *list* call is different: the board doesn't exist, so it's an error. | M1 |
| 12 | Pay collapsed to the widest band, in whole units | `job_posting` has one `pay_min`/`pay_max`; multiple bands (usually per location) become lowest-min / highest-max. Cents → whole units because nobody filters salaries by the cent. | M1 |
| 13 | Descriptions stored as plain text (jsoup) | Greenhouse sends entity-escaped HTML; scoring (M4) needs words, not tags. Raw HTML isn't kept: we can re-fetch it. | M1 |
| 14 | `remote_type` from a keyword heuristic | Location is free text ("Atlanta, Chicago, US-Remote"). "remote" beats "hybrid" beats onsite. Known misses: "Remote-friendly (NYC)", non-English text. Good enough for filtering; revisit with real data in M5. | M1 |
| 15 | `/dev/preview/{boardToken}` is temporary | Lets M1 prove the client end to end with no DB. Removed once ingest persists (M2) and the read API exists (M5). | M1 |
| 16 | ATS errors → RFC 9457 ProblemDetail (404 unknown board, 502 upstream failure) | Standard, self-describing error bodies; 502 makes it clear the failure was the job board's, not ours. | M1 |

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
