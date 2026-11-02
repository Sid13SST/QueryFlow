# Baseline (before the Reliability Lab work)

This file records the state of `main` at commit `b265d32` before any reliability
changes, so later numbers can be compared against it.

## Result

| Item | Value |
|---|---|
| Test classes | 7 |
| Tests discovered | 35 |
| Passed | 34 |
| Failed / errored | 0 / 0 |
| Skipped | 1 (`BackendApplicationTests.contextLoads`, `@Disabled` because it needs a live PostgreSQL) |
| Build | `BUILD SUCCESS` |

No tests were failing on `main`, so no fix-up commits were needed.

Per class:

| Class | Tests |
|---|---|
| `BackendApplicationTests` | 1 (skipped) |
| `BatchWriteSystemTest` | 6 |
| `CacheInvalidationServiceTest` | 6 |
| `MetricsIntegrationTest` | 5 |
| `SearchServiceTest` | 5 |
| `SuggestionServiceTest` | 5 |
| `TrendingServiceTest` | 7 |

All of these are pure unit tests (Mockito mocks for the repository and the
Redis templates). None of them needed PostgreSQL, Redis or Docker.

## How to run

```bash
cd backend
mvn -B test            # or ./mvnw test  /  .\mvnw.cmd test on Windows
```

The Surefire summary line is the source of truth:

```
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 1
[INFO] BUILD SUCCESS
```

## Environment used

| Tool | Version |
|---|---|
| JDK | OpenJDK 21.0.12 |
| Maven | 3.9.11 (the wrapper pins 3.9.16, both work) |
| Spring Boot | 4.1.0 (from `pom.xml`) |
| OS | Linux x86_64 |

## Discrepancies found while setting up

These are facts about the repository, not changes:

1. **Java version.** The README says Java 23, but `pom.xml` sets
   `<java.version>17</java.version>`. The code compiles and runs on any JDK 17+.
   JDK 23 was not available here; JDK 21 was used and **no pom change was
   needed**.
2. **Spring Boot version.** The README and the project brief describe Spring Boot 3,
   but `pom.xml` uses `spring-boot-starter-parent` **4.1.0** (Spring Framework 7,
   Spring Data Redis 4.1, Lettuce 7.5). Everything on this branch targets the
   version actually in the pom.
3. **Test count.** The README's "35 tests should pass" is accurate in the sense
   that 35 are discovered; one of them is disabled.

## Environment notes (only relevant for the Docker-based extras added later)

- The Docker daemon was not running in this container; it was started with
  `dockerd &`. Nothing in `mvn test` depends on Docker.
- Anonymous pulls from Docker Hub were rate-limited (HTTP 429). The same official
  images were pulled from Google's public mirror and re-tagged, e.g.
  `docker pull mirror.gcr.io/library/redis:7-alpine && docker tag mirror.gcr.io/library/redis:7-alpine redis:7-alpine`.
  On a normal machine `docker compose` pulls them directly.
