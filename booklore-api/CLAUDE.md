# BookLore API

Personal book/comic/audiobook library management server (Spring Boot).

## Build & Run

```bash
./gradlew build              # compile + test
./gradlew classes            # compile only (fast)
./gradlew test               # run unit tests (Mockito-based; no DB for most)
./gradlew integrationTest    # run REST API integration tests (Testcontainers MariaDB)
./gradlew bootRun            # start server (port 6060)
./gradlew bootJar            # build fat JAR
./gradlew generateJooq       # regenerate jOOQ sources (needs running MariaDB on :3366)
```

jOOQ codegen connects to `localhost:3366/booklore` by default (override with `CODEGEN_DB_URL`, `CODEGEN_DB_USER`, `CODEGEN_DB_PASS`).

## Tech Stack

- **Spring Boot 4** on Java 25 (Temurin), virtual threads enabled
- **MariaDB** (production and all tests — no H2); **Testcontainers MariaDB** for Spring-context and REST API tests
- **Flyway** for schema migrations (132 migrations in `src/main/resources/db/migration/`)
- **jOOQ** is the sole data access layer — there is no JPA/Hibernate anywhere in the codebase
- **Kotlin 2.3** + **Java** hybrid codebase
- **Lombok** on Java code, **Kotlin data classes** on new code
- **MapStruct** for DTO mapping
- **Jackson 3** (with Jackson 2 annotations compatibility)
- **Gradle 9.4** (wrapper)

## Data Access (jOOQ)

All persistence goes through jOOQ. There is no ORM, no `@Entity`, no `EntityManager`, and no JPA repository anywhere in the project — `src/main/java/org/booklore/repository/` no longer exists.

- **Repository code** lives in `src/main/kotlin/org/booklore/repository/jooq/` as Kotlin classes (`Jooq*Repository`), one per aggregate/table-group, injected with `DSLContext`
- **DTOs/row types for jOOQ repos** go in `repository/jooq/dto/` as Kotlin data classes, for query shapes that don't map 1:1 to a `model/entity` class
- **jOOQ generated sources** (tables, records) live in `src/generated/java/` (gitignored, regenerate with `./gradlew generateJooq`)
- **`model/entity/`** classes (Java, Lombok) are now plain POJOs used as read/write shapes passed between services and repositories — they carry no persistence annotations and no ORM-managed relationships. Only 9 remain (`BookEntity`, `BookFileEntity`, `BookMetadataEntity`, `ComicMetadataEntity`, `LibraryEntity`, `LibraryPathEntity`, `ShelfEntity`, `BookLoreUserEntity`, `UserPermissionsEntity`); most of the old entity graph (authors, tags, categories, moods, book reviews, comic sub-entities, etc.) was dropped in favor of jOOQ repositories returning plain rows/collections directly
- Pagination (`Page`/`Pageable`/`Sort`) uses `spring-data-commons` directly (it's a standalone dependency now, not pulled in via a JPA starter)
- `@Transactional` works the same as before — `DataSourceTransactionManager` is autoconfigured by Spring Boot off the existing `DataSource`, and jOOQ's `DSLContext` participates in the same Spring-managed transaction

## Java/Kotlin Hybrid Rules

- **Repository layer** (jOOQ): Kotlin
- **Models/entities**: Java/Lombok for now; may be rewritten to Kotlin data classes over time
- **Everything else** (services, controllers, config, etc.): stays Java unless there's a clear benefit to migrating a specific file
- Kotlin uses `-Xjsr305=strict` for null-safety interop with Spring
- `kotlin-plugin-lombok` is configured so Kotlin code can call Lombok-generated methods

## Project Structure

```
src/main/java/org/booklore/
├── app/                    # App-scoped controllers, DTOs, services (filtered views)
├── config/                 # Spring config, security config
├── controller/             # REST controllers
├── model/
│   ├── dto/                # Java DTOs
│   ├── entity/             # Plain POJOs (Lombok) - read/write shapes, no ORM
│   └── enums/
├── service/                # Business logic (organized by domain)
├── mapper/                 # MapStruct mappers
├── task/                   # Background task definitions
└── util/                   # Utilities

src/main/kotlin/org/booklore/
└── repository/jooq/        # All repositories (Kotlin, jOOQ-backed)
    └── dto/                # jOOQ-layer row/query types (Kotlin data classes)

src/generated/java/         # jOOQ codegen output (gitignored)
```

## Testing

- Unit tests (`src/test/`) are almost entirely Mockito-based with no database; one Spring-context test class (`AbstractIntegrationTest`, extended by `BookMapperTest`) boots the full context against Testcontainers MariaDB with Flyway-managed schema
- REST API integration tests live in the separate `src/integration-test/` source set (`./gradlew integrationTest`), also against Testcontainers MariaDB
- Test framework: JUnit 5, Mockito, AssertJ
- `./gradlew test` runs unit tests only (JaCoCo coverage report generated); `./gradlew integrationTest` is separate and not run by `build`

## Key Domain Concepts

BookLore manages personal libraries of books, comics, and audiobooks. Key features:
- Multi-library organization with file watching
- Metadata extraction & matching (Google Books, Goodreads, Hardcover, ComicVine)
- Multiple readers (EPUB, PDF, CBX/comics, audiobooks)
- Reading progress & session tracking
- Kobo eReader sync, KOReader sync, OPDS feeds
- Annotations, notes, bookmarks
- Smart shelves ("Magic Shelves")
- Book recommendations (vector similarity)
- Audit logging
- OIDC and remote auth support

## Conventions

- Controllers are thin; business logic lives in services
- No lazy loading / open-session-in-view concerns — jOOQ queries fetch exactly what's asked for, eagerly, in the repository method itself
- jOOQ generated fields map directly to actual DB column names (no naming-strategy translation layer)
- Server runs on port 6060 (configurable via `BOOKLORE_PORT`)
