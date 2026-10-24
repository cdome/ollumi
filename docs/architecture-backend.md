# BookLore Architecture Guide — Backend Developer

> **Audience**: Backend developers working primarily on `booklore-api`.
> Includes a light overview of the frontend so you know what to expect at the boundary.

---

## What is BookLore?

BookLore is a self-hosted, multi-user digital library. Users organize books (EPUB, PDF, CBZ/CBR, audiobooks, physical), read them in-browser, sync progress across devices (Kobo, KOReader), import via file-drop, and share via email or OPDS feeds. It ships as a single Docker image: a Spring Boot JAR serving the Angular SPA from its static resources.

---

## 1. High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│                     Single Docker Container              │
│                                                          │
│  ┌─────────────────┐        ┌──────────────────────┐     │
│  │  Angular SPA     │──API──▶│  Spring Boot 4.0.3   │     │
│  │  (static files)  │◀─WS───│  (Java 25, Tomcat)   │     │
│  └─────────────────┘        └──────────┬───────────┘     │
│                                        │                 │
│                              ┌─────────┴─────────┐      │
│                              │    MariaDB 11.4    │      │
│                              └───────────────────┘      │
└──────────────────────────────────────────────────────────┘
         ▲              ▲              ▲
    Kobo/KOReader    OPDS clients   Metadata APIs
      devices         (e-readers)   (Google, Hardcover,
                                     GoodReads, Amazon,
                                     Comicvine, Douban, ...)
```

- **Port 6060** serves both the API and the SPA.
- **WebSocket** (STOMP over `/ws`) pushes real-time events to the UI.
- **MariaDB** is the only external dependency.
- File storage is on the local filesystem (`/books`, `/bookdrop`, `/app/data`).

---

## 2. Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 25 (Virtual Threads) |
| Framework | Spring Boot | 4.0.3 |
| Build | Gradle | 9.4 |
| Data Access | jOOQ (no ORM — JPA/Hibernate was fully removed) | version managed by Spring Boot BOM |
| Database | MariaDB | 11.4 |
| Migrations | Flyway | 12.0.3 (132 SQL migrations) |
| Security | Spring Security + JWT (JJWT 0.13.0) + OAuth2/OIDC | |
| DTO Mapping | MapStruct | 1.6.3 |
| Caching | Caffeine | 3.2.3 (24h TTL) |
| PDF Processing | Apache PDFBox | 3.0.6 |
| EPUB Processing | EPUB4J | 4.2.3 |
| Archives | Commons Compress + JUnrar | 1.28.0 / 7.5.8 |
| Audio Metadata | JaudioTagger | 2.0.16 |
| Image Processing | TwelveMonkeys ImageIO | various |
| Web Scraping | JSoup | 1.22.1 |
| Templating | FreeMarker | 2.3.34 |
| WebSocket | STOMP (Spring WebSocket) | |
| API Docs | SpringDoc OpenAPI | 3.0.2 |
| JSON | Jackson 3.1.0 BOM | |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers (MariaDB) | |
| Code Reduction | Lombok | |

### Key runtime tuning

- **Virtual Threads** enabled globally (`spring.threads.virtual.enabled: true`). Tomcat uses only 20 platform threads; all request handling runs on virtual threads.
- **HikariCP**: max 10 connections, 2 min idle, 580s max lifetime.
- **jOOQ**: each repository hand-tunes its own query shape (joins, projections, batch inserts) — there's no framework-level batching/fetch-size config to tune globally, since there's no ORM session in the way.
- **Shenandoah GC** in Docker, G1GC fallback.

---

## 3. Package Structure

Root package: `org.booklore`

```
org.booklore/
├── BookloreApplication.java          # Entry point
│
├── config/                           # Spring configuration
│   ├── SecurityConfig                #   4 filter chains (OPDS, Komga, Koreader/Kobo, General)
│   ├── WebSocketConfig               #   STOMP broker + JWT interceptor
│   ├── CacheConfig                   #   Caffeine caches
│   ├── TaskExecutorConfig            #   Virtual-thread executors
│   ├── FlywayConfig                  #   Migration config
│   ├── JooqConfig                    #   jOOQ Settings bean (schema rendering)
│   ├── JacksonConfig                 #   Jackson 3 setup
│   ├── HttpClientConfig              #   JDK HttpClient beans
│   └── security/                     #   JWT utils, filters, OIDC services
│
├── controller/                       # 51+ REST controllers
│   ├── BookController                #   /api/v1/books
│   ├── EpubReaderController          #   /api/v1/epub/{id}/...
│   ├── PdfReaderController           #   /api/v1/pdf/{id}/...
│   ├── KoboController                #   /api/kobo/...
│   ├── OpdsController                #   /api/v1/opds/...
│   ├── AuthenticationController      #   /api/v1/auth/...
│   └── ...                           #   (see Section 5 for full endpoint map)
│
├── service/                          # Business logic, organized by domain
│   ├── book/                         #   16 services (CRUD, query, update, duplicate detection)
│   ├── metadata/                     #   Metadata aggregation, extraction, writing
│   │   ├── extractor/                #     Format-specific extractors (PDF, EPUB, CBX)
│   │   ├── parser/                   #     External API integrations (9 providers)
│   │   └── writer/                   #     Write metadata back to files
│   ├── kobo/                         #   15+ Kobo device sync services
│   ├── koreader/                     #   KOReader sync
│   ├── komga/                        #   Komga comic library compat
│   ├── library/                      #   Library scanning, file watching
│   ├── file/                         #   File processing, upload, streaming
│   ├── email/                        #   SMTP email distribution
│   ├── opds/                         #   OPDS feed generation
│   ├── security/                     #   Auth, OIDC, authorization
│   ├── user/                         #   User management
│   ├── event/                        #   WebSocket broadcasters
│   ├── task/                         #   Background job execution
│   ├── watcher/                      #   Filesystem monitoring
│   ├── bookdrop/                     #   Import staging
│   └── ...
│
├── model/
│   ├── entity/                       # 9 plain POJOs (Lombok) - read/write shapes, no ORM
│   ├── dto/                          # 180+ DTOs (request/, response/, kobo/, etc.)
│   ├── enums/                        # 45+ enums
│   └── websocket/                    # WebSocket message payloads
│
├── mapper/                           # MapStruct interfaces
│
├── task/                             # Background tasks (library rescan, metadata refresh, ...)
│   ├── tasks/
│   └── options/
│
├── crons/                            # Scheduled jobs (CronService, OIDC cleanup)
│
├── exception/                        # ApiError enum (50+ codes), GlobalExceptionHandler
│
├── interceptor/                      # Request interceptors
│
├── context/                          # ThreadLocal / context utilities
│
└── util/                             # FileService, FileUtils, BookUtils, ArchiveUtils, ...
```

There is no `repository/` or `converter/` package under `org.booklore` anymore — both were JPA-only and were deleted outright once their last consumer was migrated. All persistence now lives in Kotlin:

```
src/main/kotlin/org/booklore/
└── repository/jooq/                  # ~62 jOOQ repositories (DSLContext-backed), one per aggregate/table-group
    └── dto/                          # ~25 jOOQ-layer row/query types (Kotlin data classes)

src/generated/java/org/booklore/jooq/ # jOOQ codegen output: tables + records (gitignored, regenerate with ./gradlew generateJooq)
```

---

## 4. Architecture Patterns

### Layered Architecture

The codebase follows a classic **Controller → Service → Repository → Database** layering. There is no hexagonal/ports-and-adapters separation — controllers directly call services, services directly use repositories.

### DTOs & Mapping

- **Entities** (`model/entity/`) are plain POJOs (Lombok) — read/write shapes that jOOQ repositories populate and persist. They carry no persistence annotations and manage no relationships themselves; a repository method decides exactly what to attach (e.g. a book's files) on each call.
- **DTOs** (`model/dto/`) are the API contract — separate request and response types.
- **MapStruct** interfaces (`mapper/`) handle conversion. Complex mappings use `@Named` qualifiers and custom methods.
- Entities are never exposed directly to controllers.

### Service Decomposition

Large domains are split into focused services rather than one monolith:

| Domain | Services |
|---|---|
| Books | `BookService`, `BookUpdateService`, `BookQueryService`, `BookDeleteService`, `DuplicateDetectionService`, ... (16 total) |
| Metadata | `BookMetadataService` + format-specific extractors + 9 provider parsers |
| Kobo | 15+ services (sync, entitlements, reading state, collections, ...) |

### Strategy Pattern

Metadata fetching uses a strategy/factory approach:
- `MetadataProvider` enum lists providers (Google, Amazon, GoodReads, Hardcover, Comicvine, Douban, Lubimyczytac, Ranobedb, Audible).
- `BookParserConfig` registers a `BookParser` bean per provider.
- `MetadataExtractorFactory` selects the right file-format extractor.

### AOP-Based Authorization

Custom annotations enforce access control via aspects:
- `@CheckBookAccess` — verifies the user can access the book's library.
- `@CheckLibraryAccess` — verifies the user is assigned to the library.
- Implemented in `BookAccessAspect` and `LibraryAccessAspect`.

### Real-Time Events

WebSocket STOMP broadcasts push changes to connected clients:
- `AdminEventBroadcaster` — admin-scoped events.
- `BookEventBroadcaster` — book add/update/delete/cover/metadata events.
- `NotificationService` — general notifications and task progress.
- The frontend subscribes to `/user/queue/*` destinations.

### Background Tasks

- `TaskService` manages async jobs on virtual threads.
- Task types: library scan/rescan, metadata refresh, book recommendations, ...
- Cron schedules stored in `TaskCronConfigurationEntity`, managed via `CronService`.
- Progress streamed to UI via WebSocket.

---

## 5. API Surface

Base path: `/api/v1/` (some integrations use different prefixes).

### Core Endpoints

| Area | Path Prefix | Key Operations |
|---|---|---|
| **Books** | `/api/v1/books` | List, search, create, update, delete, shelve, physical books |
| **Book Content** | `/api/v1/epub/`, `/api/v1/pdf/`, `/api/v1/cbx/`, `/api/v1/audiobook/` | Stream files, page images, audio |
| **Metadata** | `/api/v1/metadata` | View, refresh, bulk update, proposals, field locks |
| **Progress** | `/api/v1/progress` | Update/reset reading progress per book |
| **Shelves** | `/api/v1/shelves`, `/api/v1/magic-shelves` | CRUD, book assignment, dynamic shelves |
| **Libraries** | `/api/v1/libraries` | CRUD, scan, rescan, health |
| **Users** | `/api/v1/users` | CRUD, permissions |
| **Auth** | `/api/v1/auth` | Login (JWT), refresh, OIDC init |
| **Settings** | `/api/v1/settings` | App and user settings |
| **Email** | `/api/v1/email`, `/api/v1/email-providers` | Send books, SMTP config |
| **OPDS** | `/api/v1/opds`, `/api/v2/opds` | E-reader feed discovery, search |
| **Bookdrop** | `/api/v1/bookdrop` | Import staging |
| **Notes/Annotations** | `/api/v1/notes`, `/api/v1/marks`, `/api/v1/annotations` | User content |
| **Stats** | `/api/v1/stats` | Reading analytics |
| **Tasks** | `/api/v1/tasks` | Background job status |
| **Audit** | `/api/v1/audit-log` | User action trail |

### Integration Endpoints

| Integration | Path Prefix | Auth |
|---|---|---|
| **Kobo Sync** | `/api/kobo/` | KoboAuthFilter (device token) |
| **KOReader Sync** | `/api/koreader/` | KoreaderAuthFilter |
| **Komga Compat** | `/komga/api/v1/` | HTTP Basic |
| **OPDS** | `/api/v1/opds/` | HTTP Basic |
| **Media** | `/api/v1/media/` | JWT via query param |

### Error Handling

- `ApiError` enum defines 50+ error codes, each mapped to an HTTP status.
- `GlobalExceptionHandler` catches all exceptions and returns `ErrorResponse` DTOs.
- Validation uses Jakarta annotations (`@Valid`, `@NotNull`, `@Size`).

---

## 6. Database & Persistence

### Schema

MariaDB with 132 Flyway migrations (all raw SQL, in `db/migration/`).

### Core Data Model

There is no ORM-managed entity graph. `model/entity/` holds 9 plain POJOs that jOOQ repositories populate on each call — a repository method decides exactly what to attach (e.g. whether to include a book's files), rather than a framework following declared relationships lazily or eagerly. Most of the old per-row entities (authors, tags, categories, moods, book reviews, comic creators/characters/teams, etc.) don't exist as Java types anymore; a jOOQ repository reads them directly into a flat collection (e.g. `Set<String> authorNames`) on the parent POJO, or returns them as a standalone row/DTO type from `repository/jooq/dto/`.

```
BookLoreUserEntity (users)
└── 1:1  UserPermissionsEntity (28 boolean permission flags, users.id = user_permissions.user_id)
    (shelves, assigned libraries, settings, KOReader/Kobo linkage, etc. are all queried
     directly from their own tables by dedicated jOOQ repositories - not held on the user POJO)

LibraryEntity (library)
    (library_path and book rows are queried by library_id via their own jOOQ repositories,
     not held as collections on LibraryEntity)

LibraryPathEntity (library_path)
└── library: LibraryEntity        # the path's owning library, attached by the repository

BookEntity (book)
├── metadata: BookMetadataEntity  # 1:1, book.id = book_metadata.book_id
│   ├── authorNames / categoryNames / moodNames / tagNames: flat String collections
│   │     (read from their junction tables directly - no entity type per taxonomy)
│   └── comicMetadata: ComicMetadataEntity   # optional - comics/manga only
│         └── characterNames / teamNames / locationNames / pencillerNames / ...: flat String collections
├── library: LibraryEntity
├── libraryPath: LibraryPathEntity
└── bookFiles: List<BookFileEntity>   # primary + alternative formats

ShelfEntity (shelf)
├── user: BookLoreUserEntity      # id-only stub on most hydration paths - see repository for exact shape
└── bookCount: int                # computed via a correlated subquery in the jOOQ SELECT, not a stored column
    (book_shelf_mapping is a pure junction table, read/written by JooqShelfRepository -
     ShelfEntity holds no in-memory list of books)
```

Everything not shown above (reading progress, notes, annotations, bookmarks, Kobo/KOReader sync state, email providers, audit log, OIDC sessions, magic shelves, recommendations, ...) was never promoted to a `model/entity` POJO at all — those domains are jOOQ-only from the start: a `Jooq*Repository` in Kotlin reading/writing its table(s) directly into request/response DTOs or small `repository/jooq/dto/` row types, with nothing in `model/entity/` representing them.

### Data Access Patterns

- **jOOQ repositories** (`src/main/kotlin/org/booklore/repository/jooq/`, one per aggregate/table-group) take a `DSLContext` and expose plain methods (`findById`, `save`, `findAllWithMetadataByLibraryId`, ...) — no derived-query magic, no JPQL, every query is explicit.
- **No lazy loading**: every repository method fetches exactly what it returns, in the method body, with explicit joins. There's no proxy, no N+1 surprise from touching an unfetched relation later — if a caller needs a book's files, the repository method it called either already joined them or it didn't, and that's visible in the Kotlin source.
- **Transactions**: `@Transactional` (Spring's own annotation, not JPA's) with `readOnly` on query methods, same as before. Spring Boot autoconfigures a `DataSourceTransactionManager` off the app's `DataSource`, and jOOQ's `DSLContext` participates in that same Spring-managed transaction — no separate transaction manager wiring needed.
- **Upserts**: a repository's `save()` typically checks whether the POJO's `id` is null to decide insert-vs-update (mirroring the insert-or-update semantics Spring Data `save()` used to provide), then issues the corresponding `INSERT ... RETURNING id` or `UPDATE ... WHERE id = ?`.
- **1:1 children** (e.g. `UserPermissionsEntity`, `BookMetadataEntity`) are upserted by the same repository call that upserts their parent, matching the old cascade-on-save behavior without an actual cascade mechanism.

### History: migration off JPA/Hibernate

BookLore originally used Spring Data JPA/Hibernate end-to-end. A phased migration ("Phase 10" internally) moved every entity aggregate — Book, LibraryPath, Shelf, Library, User+UserPermissions, plus all the smaller domains (authors/tags/categories/moods, comic metadata, reviews, shelf/library-user mappings, reading progress, Kobo/KOReader sync, etc.) — to jOOQ one vertical slice at a time, and finished by removing `spring-boot-starter-data-jpa` and the Hibernate Gradle plugin from `build.gradle` entirely. Two dependencies that had only ever been present *transitively* via the JPA starter needed to be added back explicitly once it was gone: `spring-data-commons` (for `Page`/`Pageable`/`Sort`, used throughout the API layer for pagination) and `spring-boot-starter-aspectj` (Spring Boot 4.0's rename of `spring-boot-starter-aop`, needed by the two AOP access-check aspects in §4). The migration is complete — there is no remaining JPA code, no H2, and no Hibernate dependency anywhere in the project.

---

## 7. Security

### Authentication

| Mechanism | Used By | Implementation |
|---|---|---|
| **JWT** | Browser SPA, WebSocket | `JwtAuthenticationFilter`, JJWT library |
| **JWT (query param)** | Media/cover/font streaming | `CoverJwtFilter`, `EpubStreamingJwtFilter`, `CustomFontJwtFilter` |
| **OIDC** | External IdP users | Full OAuth2 client flow, discovery, group mapping |
| **HTTP Basic** | OPDS, Komga | Spring Security Basic auth |
| **Device Token** | Kobo, KOReader | `KoboAuthFilter`, `KoreaderAuthFilter` |
| **Header Auth** | Reverse proxy setups | Configurable header-based auth (disabled by default) |

### Authorization

- **RBAC**: `UserPermissionsEntity` stores 28 boolean permission flags (upload, download, edit metadata, manage library, email, delete, sync, OPDS, bookdrop, stats, tasks, bulk operations, ...).
- **Library-scoped access**: Users see only assigned libraries. Admins see all.
- **AOP enforcement**: `@CheckBookAccess` / `@CheckLibraryAccess` annotations with aspects.
- **Content restrictions**: Age-rating based filtering per user.

### Security Config

`SecurityConfig.java` defines 4 ordered `SecurityFilterChain` beans:
1. OPDS endpoints (Basic auth)
2. Komga endpoints (Basic auth)
3. KOReader/Kobo endpoints (custom filters)
4. General API + media endpoints (JWT)

CSRF disabled (stateless API). CORS configurable via `ALLOWED_ORIGINS` env var.

---

## 8. Configuration

### Key application.yaml Settings

```yaml
server.port: 6060
app.path-config: /app/data       # thumbnails, caches, fonts
app.bookdrop-folder: /bookdrop   # watched import folder
app.disk-type: LOCAL             # LOCAL or NETWORK (read-only mode)

spring.datasource.url: jdbc:mariadb://...
spring.threads.virtual.enabled: true
spring.flyway.enabled: true
spring.jooq.sql-dialect: MARIADB
```

### Environment Variables

| Variable | Purpose |
|---|---|
| `DATABASE_URL` / `DATABASE_HOST` / `DATABASE_PORT` | DB connection |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials |
| `BOOKLORE_PORT` | Server port (default 6060) |
| `ALLOWED_ORIGINS` | CORS origins |
| `DISK_TYPE` | `LOCAL` or `NETWORK` |
| `REMOTE_AUTH_ENABLED` | Header-based auth toggle |
| `FORCE_DISABLE_OIDC` | Disable OIDC |
| `LOG_LEVEL` / `ROOT_LOG_LEVEL` | Logging |
| `REMOTE_DEBUG_ENABLED` | JDWP on port 5005 |

---

## 9. Testing

- **JUnit 5** + **Spring Boot Test** for integration tests.
- **Mockito** for unit-level mocking — the large majority of `src/test/` is pure Mockito unit tests with no database at all, since jOOQ repositories are just constructor-injected collaborators that get mocked like any other.
- **AssertJ** for fluent assertions.
- **Testcontainers (MariaDB)** — there is no H2 anywhere in the project. One Spring-context test class (`AbstractIntegrationTest`, under `src/test/`) boots the full app against a MariaDB Testcontainer with Flyway-managed schema; the separate `src/integration-test/` source set (run via `./gradlew integrationTest`) does the same for full REST API tests, driving the app over HTTP end-to-end.
- **JaCoCo** for coverage reports (separate reports for `test` and `integrationTest`, plus a combined report task).
- Test areas: jOOQ repositories, services, controllers, utilities, format-specific logic, security.

---

## 10. External Integrations

### Metadata Providers (HTTP)

| Provider | Method | Notes |
|---|---|---|
| Google Books | REST API | Primary provider |
| Hardcover.com | GraphQL | Custom DTO parsing |
| GoodReads | Web scraping (JSoup) | No official API |
| Amazon | Product API | |
| Comicvine | REST API | Comics/manga |
| Douban | REST API | Chinese books |
| Lubimyczytac | Web scraping | Polish books |
| Ranobedb | REST API | Light novels |
| Audible/Audnexus | REST API | Audiobooks |

### Device Sync

| Device | Protocol | Notes |
|---|---|---|
| Kobo | Kobo cloud API (OAuth2) | Reading state, entitlements, collections |
| KOReader | Custom REST protocol | Device ID-based auth |
| Komga | Komga-compatible REST API | Comic library compatibility |

### Distribution

| Channel | Implementation |
|---|---|
| Email | Configurable SMTP (EmailProviderV2Entity) |
| OPDS | Feed generation (discovery, search, acquisition) |

### No Message Brokers

There is no Kafka, RabbitMQ, or external queue. All async work uses in-process virtual threads. Real-time events use WebSocket STOMP only.

---

## 11. Maintainability Assessment

### Strengths

- **Clean layering** — Controller/Service/Repository separation is consistent.
- **Comprehensive DTO layer** — API contracts are well-isolated from persistence.
- **Virtual threads** — Modern concurrency without callback complexity.
- **Flyway migrations** — Schema evolution is tracked and reproducible.
- **Domain decomposition** — Large domains (books, metadata, Kobo) split into focused services.
- **AOP authorization** — Access checks are declarative, not scattered through business logic.
- **Centralized error handling** — `ApiError` enum + `GlobalExceptionHandler` gives uniform API errors.
- **No ORM, no lazy-loading surprises** — jOOQ repositories fetch exactly what they return; there's no proxy, no open-session-in-view, no N+1 from touching an unfetched relation later. What a repository method joins is visible in its Kotlin source, full stop.
- **Small, flat entity model** — `model/entity/` is down to 9 plain POJOs (from 70+ pre-migration); most domains were never promoted to an entity at all and are jOOQ-only, reading/writing rows directly into DTOs.

### Areas to Watch

- **Service fan-out** — some services (especially book-related: 16 services) have fine-grained splits that can make it hard to find where logic lives.
- **Web scraping integrations** (GoodReads, Lubimyczytac) — fragile by nature; expect maintenance burden.
- **No formal API versioning** — endpoints are `/v1/` but there's no migration strategy documented.
- **jOOQ repositories decide hydration per call site** — the flexibility that avoids N+1 also means there's no single declared "shape" of e.g. a `BookEntity`; different repository methods can return it with or without `bookFiles`/`metadata` attached depending on what their caller needs. Worth checking the specific method signature rather than assuming the entity is always fully populated.

---

## 12. Frontend Overview (for context)

The frontend is an **Angular 21 SPA** (`booklore-ui/`) using **PrimeNG** as the UI component library.

### What you need to know as a backend dev

- **API contract**: The frontend calls `/api/v1/*` endpoints. All request/response shapes must match the DTOs in `model/dto/`.
- **Auth flow**: Frontend stores JWT in localStorage, sends it via `Authorization: Bearer` header. An HTTP interceptor handles 401 → token refresh → retry automatically.
- **WebSocket**: Frontend subscribes to `/user/queue/*` STOMP destinations. When you broadcast via `BookEventBroadcaster` or `NotificationService`, the UI reacts immediately (updates book lists, shows progress bars, toasts).
- **File streaming**: Covers, EPUB resources, PDFs, and fonts are streamed with JWT passed as a query parameter (not header) — this is why separate JWT filters exist for media endpoints.
- **State is service-based**: No NgRx or central store. Each feature has its own service with `BehaviorSubject` observables. Book list state, library state, and auth state are the main ones.
- **PWA**: The app uses Angular Service Worker. API calls are configured as network-first (no caching). Static assets are cached for offline shell.
- **i18n**: 18 languages via Transloco. Translation keys live in `booklore-ui/src/i18n/{lang}/`.
- **Readers**: Four in-browser readers (PDF via ngx-extended-pdf-viewer, EPUB custom, CBX custom, audiobook custom). Each reader saves progress via the `/api/v1/progress` endpoint.

### Dev setup

Frontend runs on port 4200 in dev, proxying API calls to backend on port 6060. The `dev.docker-compose.yml` runs both side by side. In production, the Angular build output is embedded into the Spring Boot JAR as static resources.
