# booklore-api performance tests

Gatling-based performance tests for a **running** booklore-api instance, seeded with a
realistic dataset (default: **10,000 books / 1,000 users / 100 concurrent users**).

This is a standalone Gradle build — it is deliberately *not* part of the root
`booklore-api` project. See `../PERF_TEST_PLAN.md` for the design.

## Prerequisites

- A running booklore-api instance (default: `http://localhost:6060`)
- Network access to its MariaDB (default: `localhost:3366`, db `booklore`, user/pass `booklore`)
- JDK 17+ (project compiles with `--release 17`)

## Quick start

```bash
cd perf-tests

# 1. Seed the database (idempotent guard — run cleanData first on a re-run)
./gradlew seedData

# 2. Sanity check (~1 min, 6 users, one per scenario group)
./gradlew gatlingRun-SmokeSimulation

# 3. Full load test (100 concurrent users, 2 min ramp + 10 min steady state)
./gradlew gatlingRun-MixedLoadSimulation

# Reset everything (removes perf-* users/libraries and cascaded data)
./gradlew cleanData

# Seed + full run in one shot
./gradlew perfTest
```

Reports: `build/reports/gatling/<simulation>-<timestamp>/index.html`

## Pointing at another environment

Everything is a `-D` system property (env vars in parentheses also work):

```bash
./gradlew seedData \
  -Dperf.baseUrl=https://staging.example.com \
  -Dperf.db.url='jdbc:mariadb://staging-db:3306/booklore?rewriteBatchedStatements=true' \
  -Dperf.db.user=booklore -Dperf.db.password=secret

./gradlew gatlingRun-MixedLoadSimulation -Dperf.baseUrl=https://staging.example.com
```

| Property | Default | Meaning |
|---|---|---|
| `perf.baseUrl` | `http://localhost:6060` | App base URL (tests + bootstrap) |
| `perf.db.url` | `jdbc:mariadb://localhost:3366/booklore?...` | JDBC URL used by the seeder |
| `perf.db.user` / `perf.db.password` | `booklore` / `booklore` | DB credentials |
| `perf.userCount` / `perf.bookCount` | `1000` / `10000` | Dataset size |
| `perf.concurrentUsers` | `100` | Concurrent virtual users in MixedLoadSimulation |
| `perf.rampUp` / `perf.duration` | `2m` / `10m` | Load profile (`30s`, `2m`, `1h`, or ISO-8601) |
| `perf.user.password` | `PerfUser-Passw0rd!` | Shared password of all seeded perf users |
| `perf.threshold.pagedMs` | `5000` | p95 assertion for paged/filtered/search endpoints |
| `perf.threshold.fullListMs` | `8000` | p95 assertion for the unpaginated `GET /api/v1/books` |
| `perf.threshold.successPercent` | `99.0` | Global success-rate assertion |

Threshold defaults are calibrated to the local-dev baseline (10k books / 100 users,
app + DB on one machine: paged p95 ≈ 3.5 s, full list p95 ≈ 6.7 s) — tighten them as
the app gets faster.

## Notes

- **Gatling 3.15.0 / plugin 3.15.0** — 3.15.0+ is required for Gradle 9
  (older plugin versions use `Project.reportsDir`, removed in Gradle 9).
- `forever()` scenario loops are capped with `maxDuration` (ramp + duration + drain).
- Perf users get `permission_access_user_stats=true` — required by `/api/v1/user-stats/**`.
- Login is rate-limited server-side: simulations pre-fetch JWTs with pacing in `before()`.

## What gets seeded

All rows are marked and removable: users `perf-user-0001..N` (+ admin `perf-admin` via the
public setup wizard when the instance is empty), libraries `perf-library-1..3`,
dictionaries `Perf Author/Category/Tag *`. Books carry metadata, one `book_file` row each
(EPUB/PDF/CBX/AUDIOBOOK ≈ 60/25/10/5%), ~half in series of 5. Every user has a `Favorites`
shelf with books, ~50 progress rows and ~75 reading sessions spread over the last 2 years.
ID ranges land in `build/seed-manifest.properties`, which the simulations' feeders read.

## Layout

- `config/TestConfig` — all knobs above
- `seed/DataSeeder` / `seed/DataCleaner` — JDBC bulk seed/cleanup
- `feed/Feeders`, `chain/AuthSupport`, `config/PerfContext` — session data + JWT handling
- `scenario/*` — six read-path groups; `simulation/*` — Smoke + MixedLoad
