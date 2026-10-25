# BookLore Architecture Guide — Frontend Developer

> **Audience**: Frontend developers working primarily on `booklore-ui`.
> Includes a light overview of the backend so you know what to expect at the boundary.

---

## What is BookLore?

BookLore is a self-hosted, multi-user digital library. Users organize books (EPUB, PDF, CBZ/CBR, audiobooks, physical), read them in-browser, sync progress across devices (Kobo, KOReader), import via file-drop, and share via email or OPDS feeds. It ships as a single Docker image: the Angular SPA is embedded into the Spring Boot JAR as static resources.

---

## 1. High-Level Architecture

```
┌──────────────────────────────────────────────────────┐
│                  Single Docker Container             │
│                                                      │
│  ┌─────────────────┐       ┌──────────────────────┐  │
│  │  Angular 21 SPA  │──REST─▶│  Spring Boot 4.0.3  │  │
│  │  (PrimeNG UI)    │◀──WS──│  (Java 25)          │  │
│  └─────────────────┘       └──────────┬───────────┘  │
│                                       │              │
│                             ┌─────────┴─────────┐   │
│                             │   MariaDB 11.4    │   │
│                             └───────────────────┘   │
└──────────────────────────────────────────────────────┘
```

- **Dev**: Angular runs on port **4200**, backend on port **6060**. API calls proxied to backend.
- **Prod**: Angular build output is served by Spring Boot from port **6060** — single origin, no CORS issues.
- **WebSocket** (STOMP over `/ws`) delivers real-time events from server to UI.

---

## 2. Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Framework | Angular | 21.2.4 |
| Language | TypeScript | 5.9.3 (strict mode) |
| Build | @angular/build (Vite-based) | |
| UI Library | PrimeNG | 21.1.1 |
| Icons | PrimeIcons | 7.0.0 |
| Theming | @primeuix/themes (Aura preset) | 2.0.3 |
| Reactive | RxJS | 7.8.2 |
| WebSocket | @stomp/rx-stomp + @stomp/stompjs | 2.3.0 / 7.3.0 |
| PDF Reader | ngx-extended-pdf-viewer | 25.6.4 |
| Charts | ng2-charts + Chart.js | 8.0.0 / 4.5.1 |
| Virtual Scroll | @iharbeck/ngx-virtual-scroller | 20.0.0 |
| Infinite Scroll | ngx-infinite-scroll | 21.0.0 |
| Lazy Images | ng-lazyload-image | 9.1.3 |
| i18n | @jsverse/transloco | 8.2.1 |
| Dates | date-fns | 4.1.0 |
| JWT | jwt-decode | 4.0.0 |
| Sanitization | DOMPurify | 3.3.1 |
| Markdown | showdown | 2.1.0 |
| Testing | Vitest + @analogjs/vitest-angular | 4.0.17 |
| Linting | angular-eslint + typescript-eslint | 21.2.0 / 8.56.1 |

---

## 3. Project Structure

```
src/
├── app/
│   ├── app.component.ts              # Root — WebSocket subscriptions, offline detection
│   ├── app.config.ts                 # Providers (router, HTTP, i18n, service worker)
│   ├── app.routes.ts                 # Top-level routing
│   │
│   ├── core/                         # Singleton services, global concerns
│   │   ├── config/
│   │   │   ├── api.config.ts         #   Base URL for REST + WebSocket
│   │   │   ├── transloco-loader.ts   #   Lazy translation file loader
│   │   │   └── language-initializer.ts
│   │   ├── security/
│   │   │   ├── auth.service.ts       #   JWT token management
│   │   │   ├── oidc.service.ts       #   OIDC redirect/callback
│   │   │   ├── auth-guard.ts         #   Route guard (token validity)
│   │   │   └── auth-interceptor.ts   #   HTTP interceptor (add Bearer, refresh on 401)
│   │   └── services/
│   │       ├── loading.service.ts    #   Global loading state
│   │       └── post-login-initializer.ts
│   │
│   ├── features/                     # Feature areas (lazy-loaded)
│   │   ├── book/                     #   Core book browsing & management
│   │   │   ├── components/           #     BookBrowserComponent, BookCardComponent, ...
│   │   │   ├── service/              #     BookService (state), LibraryService, ShelfService
│   │   │   └── model/               #     Book, Shelf, Library types
│   │   ├── readers/
│   │   │   ├── pdf-reader/           #     PDF viewing (ngx-extended-pdf-viewer)
│   │   │   ├── ebook-reader/         #     EPUB reader (custom implementation)
│   │   │   ├── cbx-reader/           #     Comic/manga reader
│   │   │   └── audiobook-player/     #     Audio playback with chapters
│   │   ├── dashboard/                #   Home — scrollers for recent, reading, magic shelves
│   │   ├── metadata/                 #   Metadata viewer/editor, bulk operations
│   │   ├── bookdrop/                 #   File upload & review for bulk import
│   │   ├── settings/                 #   19 sub-features (readers, account, admin, ...)
│   │   ├── stats/                    #   Reading analytics (charts)
│   │   ├── notebook/                 #   Notes & highlights across books
│   │   ├── author-browser/           #   Browse/view authors
│   │   ├── series-browser/           #   Browse/view series
│   │   ├── magic-shelf/              #   Dynamic rule-based shelves
│   │   └── library-creator/          #   Library management
│   │
│   └── shared/                       # Reusable across features
│       ├── components/               #   Login, setup wizard, icon picker, dialogs
│       ├── layout/                   #   AppLayoutComponent, topbar, sidebar
│       ├── service/                  #   25+ domain services (metadata, progress, email, ...)
│       ├── websocket/                #   STOMP connection management
│       ├── model/                    #   Shared TypeScript interfaces/types
│       ├── styles/                   #   SCSS variables, mixins
│       └── validators/               #   Custom form validators
│
├── i18n/                             # Translation files
│   ├── en/                           #   English (inline, not lazy)
│   ├── es/, de/, fr/, ...            #   18 languages total (lazy-loaded)
│
├── assets/                           # Static assets, images
├── styles.scss                       # Global styles, PrimeNG overrides
└── main.ts                           # Bootstrap
```

### Key conventions

- **Standalone components everywhere** — no NgModules. `angular.json` defaults `standalone: true`.
- **`inject()` function** for DI — no constructor injection.
- **Feature-based organization** — each feature owns its components, services, and models.
- **Lazy loading** via `loadComponent` in routes (8 routes).

---

## 4. Routing

### Route Map

```
/                           → SetupRedirectGuard → /dashboard or /setup
/login                      → LoginComponent (LoginGuard)
/setup                      → SetupComponent (SetupGuard — only if no admin exists)
/oauth2-callback            → OIDC callback handler
/change-password            → Password change

── AppLayoutComponent (sidebar + topbar shell) ──
  /dashboard                → MainDashboardComponent
  /all-books                → BookBrowserComponent
  /library/:libraryId/books → BookBrowserComponent (library-scoped)
  /shelf/:shelfId/books     → BookBrowserComponent (shelf-scoped)
  /unshelved-books           → BookBrowserComponent (unshelved filter)
  /magic-shelf/:id/books    → BookBrowserComponent (magic shelf)
  /book/:bookId             → BookMetadataCenterComponent (lazy)
  /series                   → SeriesBrowserComponent (lazy)
  /series/:seriesName       → SeriesDetailComponent (lazy)
  /authors                  → AuthorBrowserComponent (lazy)
  /author/:authorId         → AuthorDetailComponent (lazy)
  /settings                 → SettingsComponent (lazy)
  /metadata-manager         → MetadataManagerComponent (lazy, guarded)
  /bookdrop                 → BookdropComponent (lazy, guarded)
  /library-stats            → LibraryStatsComponent (lazy, guarded)
  /reading-stats            → ReadingStatsComponent (lazy, guarded)
  /notebook                 → NotebookComponent (lazy)

── Outside layout (full-screen readers) ──
  /pdf-reader/book/:bookId       → PdfReaderComponent (lazy)
  /ebook-reader/book/:bookId     → EbookReaderComponent (lazy)
  /cbx-reader/book/:bookId       → CbxReaderComponent (lazy)
  /audiobook-player/book/:bookId → AudiobookPlayerComponent (lazy)

**                          → fallback to /login
```

### Route Guards

| Guard | Purpose |
|---|---|
| `AuthGuard` | Checks JWT token exists and isn't expired. Redirects to `/login`. |
| `SetupGuard` | Only allows `/setup` if no admin user exists yet. |
| `SetupRedirectGuard` | Root `/` redirect — setup if needed, dashboard otherwise. |
| `LoginGuard` | Prevents showing login when already authenticated. |
| `BookdropGuard` | Requires upload permission. |
| `EditMetadataGuard` | Requires metadata editing permission. |
| `LibraryStatsGuard` | Requires stats access permission. |
| `UserStatsGuard` | Requires personal stats permission. |

---

## 5. State Management

There is **no central state library** (no NgRx, Akita, NGXS). State is managed through **service-based observables**.

### Core State Services

| Service | State | Pattern |
|---|---|---|
| **BookStateService** | `bookState$: Observable<{books, loaded, error}>` | BehaviorSubject, auto-loads on first subscribe |
| **LibraryService** | `libraryState$` (library list) | BehaviorSubject |
| **AuthService** | `token$: Observable<string \| null>` | BehaviorSubject, localStorage persistence |
| **AppConfigService** | `appState = signal<AppState>({})` | Angular signal + localStorage via `effect()` |
| **MetadataProgressService** | Metadata batch update progress | WebSocket-driven |
| **TaskService** | Background task progress | WebSocket-driven |
| **ReaderStateService** | Reader UI state (annotations, bookmarks, sidebar) | Per-reader instance |

### Patterns Used

- **BehaviorSubject** for stateful observables (emit last value to new subscribers).
- **`shareReplay(1)`** for caching HTTP results.
- **`takeUntil(destroy$)`** for cleanup in `ngOnDestroy`.
- **`distinctUntilChanged()`** for deduplication.
- **`tap()`** for side effects inside pipes.
- **Immutable updates** via spread operator (`{ ...state, books: newBooks }`).
- **localStorage** for user preferences and tokens.

---

## 6. Component Architecture

### Smart vs Presentational

| Type | Examples | Responsibilities |
|---|---|---|
| **Smart (container)** | `BookBrowserComponent`, `EbookReaderComponent`, `BookMetadataCenterComponent`, `MainDashboardComponent` | Manages state, calls services, orchestrates children |
| **Presentational** | `BookCardComponent`, `BookTableComponent`, `MetadataViewerComponent`, `TextSelectionPopupComponent` | Renders data, emits events, no service injection |

### Communication Patterns

| Pattern | Usage |
|---|---|
| `@Input()` | Parent → child data binding |
| `@Output()` + EventEmitter | Child → parent events |
| Shared services | Cross-component communication |
| `DynamicDialogRef` (PrimeNG) | Dialog result communication |
| Route params | `ActivatedRoute.paramMap` / `queryParamMap` |

### BookBrowserComponent — the central workhorse

This is the most complex component. It handles:
- Grid and table view modes (switchable).
- Virtual scrolling for performance with large libraries.
- Multi-select for bulk actions.
- Filtering by shelf, series, author, read status, rating, file type.
- Sorting by title, author, date added, read date, rating.
- Context shared across `/all-books`, `/library/:id/books`, `/shelf/:id/books`, `/unshelved-books`, `/magic-shelf/:id/books` — same component, different data source based on route.

---

## 7. API Communication

### HTTP Service Pattern

Every domain has a dedicated service wrapping `HttpClient`:

```typescript
@Injectable({ providedIn: 'root' })
export class BookService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private http = inject(HttpClient);

  getBooks(params: HttpParams): Observable<Book[]> {
    return this.http.get<Book[]>(this.url, { params });
  }
}
```

### Services (25+)

Core: `BookService`, `LibraryService`, `ShelfService`, `UserService`, `SettingsService`, `MetadataService`, `BookdropFileService`, `ReadingSessionService`, `AnnotationService`, and feature-specific services.

### Auth Interceptor (`AuthInterceptorService`)

Every outgoing HTTP request gets `Authorization: Bearer {token}` added automatically.

On **401 response**:
1. Blocks additional requests (queues them).
2. Calls `/api/v1/auth/refresh` with the refresh token.
3. On success: retries original + all queued requests with new token.
4. On failure: forces logout.

### Base URL Configuration (`api.config.ts`)

| Environment | REST Base | WebSocket |
|---|---|---|
| Dev | `http://localhost:6060` | `ws://localhost:6060/ws` |
| Prod | `window.location.origin` | `wss://{host}/ws` |

### Error Handling

- 401 → interceptor handles (refresh or logout).
- Other errors → `MessageService` toast notification (PrimeNG).
- Form errors → inline validation messages.

---

## 8. WebSocket & Real-Time

### Connection

STOMP over WebSocket via `@stomp/rx-stomp`. Connects to `/ws` with JWT in STOMP headers.

### Subscriptions (in `app.component.ts`)

| Destination | Event | UI Response |
|---|---|---|
| `/user/queue/book-add` | Books added (with batch progress) | Update book list, show progress |
| `/user/queue/book-update` | Single book updated | Patch book in state |
| `/user/queue/books-cover-update` | Covers changed | Refresh cover images |
| `/user/queue/books-remove` | Books deleted | Remove from state |
| `/user/queue/book-metadata-update` | Single metadata update | Patch metadata |
| `/user/queue/book-metadata-batch-update` | Batch metadata complete | Refresh metadata |
| `/user/queue/book-metadata-batch-progress` | Batch progress | Update progress bar |
| `/user/queue/log` | Server log/notification | Show toast |
| `/user/queue/bookdrop-file` | Bookdrop file status | Update import UI |
| `/user/queue/task-progress` | Background task progress | Update task UI |
| `/user/queue/session-revoked` | Forced logout | Clear tokens, redirect to login |

All subscriptions are user-scoped (`/user/queue/`) — each user only receives their own events.

---

## 9. UI Library & Theming

### PrimeNG 21.1.1

The entire UI is built on PrimeNG components:
- Layout: Sidebar, Toolbar, Panel, Card, Divider
- Data: DataTable, VirtualScroller, Tree
- Forms: InputText, Dropdown, MultiSelect, Checkbox, Slider, Calendar
- Feedback: Toast, ConfirmDialog, Dialog, ProgressBar
- Navigation: Menu, Breadcrumb, TabView

### Theme System

- **Aura preset** — modern, professional look.
- **20 surface palettes** (slate, zinc, midnight-blue, charcoal, soho, ...).
- **10+ primary color options** (green is default).
- **Dark mode** enabled by default (`.p-dark` class on `<html>`).
- Theme switching via `AppConfigService` — persists to localStorage.

### Styling

- **SCSS** throughout.
- Global styles in `src/styles.scss` — PrimeNG overrides, book-type colors, scrollbar styling.
- Component-scoped styles in `.component.scss` files with ViewEncapsulation.
- CSS variables for dynamic theming.
- Responsive design with CSS Grid/Flexbox.

### Build Budgets

- Initial bundle: **5.75 MB warning**, **6 MB error**.
- Per-component style: **25 kB max**.

---

## 10. Readers

Four in-browser readers, each a full-screen route outside the main layout:

| Reader | Route | Library | Key Features |
|---|---|---|---|
| **PDF** | `/pdf-reader/book/:bookId` | ngx-extended-pdf-viewer | Annotations, zoom, page nav, printing |
| **EPUB** | `/ebook-reader/book/:bookId` | Custom implementation | CFI-based bookmarks, custom fonts, per-book settings, highlights, annotations |
| **Comic (CBX)** | `/cbx-reader/book/:bookId` | Custom implementation | Page spread modes, fit modes, background colors |
| **Audiobook** | `/audiobook-player/book/:bookId` | Custom implementation | Chapter navigation, playback speed, time scrubbing |

All readers save progress via `/api/v1/progress` endpoints. Progress syncs across devices (Kobo, KOReader) through the backend.

---

## 11. Forms

### Approach

**Reactive Forms** for complex forms (setup wizard, settings, metadata editor):
```typescript
this.form = this.fb.group({
  name: ['', [Validators.required]],
  email: ['', [Validators.required, Validators.email]],
  password: ['', [Validators.required, Validators.minLength(8)]],
  confirmPassword: ['', [Validators.required]],
}, { validators: [passwordMatchValidator('password', 'confirmPassword')] });
```

**Template-driven Forms** for simple inputs (login, password change) — `[(ngModel)]`.

### Custom Validators

- `passwordMatchValidator` — cross-field password confirmation.
- Component-level validators for specific business rules.

---

## 12. Authentication Flow

### Internal Auth

```
Login Form → POST /api/v1/auth/login
           → { accessToken, refreshToken, isDefaultPassword }
           → Store in localStorage
           → If isDefaultPassword → redirect to /change-password
           → Else → redirect to /dashboard
```

### OIDC Auth

```
Click "Login with OIDC" → OidcService redirects to provider
                        → Provider callback to /oauth2-callback
                        → Exchange code for tokens
                        → Store tokens, redirect to /dashboard
```

### Token Storage

| Key | Storage | Purpose |
|---|---|---|
| `accessToken_Internal` | localStorage | JWT access token |
| `refreshToken_Internal` | localStorage | JWT refresh token |

### Token Validation (AuthGuard)

```typescript
const payload = JSON.parse(atob(token.split('.')[1]));
if (payload.exp * 1000 < Date.now()) → redirect to /login
if (payload.isDefaultPassword) → redirect to /change-password
```

---

## 13. Internationalization (i18n)

### Transloco (18 languages)

| Setup | Detail |
|---|---|
| Default language | English (`en`) — inline (not lazy-loaded) |
| Other languages | `es`, `it`, `de`, `fr`, `nl`, `pl`, `pt`, `ru`, `hr`, `sv`, `zh`, `ja`, `hu`, `sl`, `sk`, `uk`, `id`, `da` — lazy-loaded on demand |
| Translation files | `src/i18n/{lang}/` — multiple domain JSON files per language |
| Merge strategy | Deep merge — language overrides English defaults |

### Usage

```html
<!-- Pipe -->
{{ 'dashboard.main.pageTitle' | transloco }}

<!-- Structural directive -->
<p *transloco="let t; prefix: 'app'">{{ t('loading') }}</p>
```

```typescript
// In code
private t = inject(TranslocoService);
this.t.translate('auth.login.sessionRevoked');
```

Translations managed on [Weblate](https://hosted.weblate.org/engage/booklore/).

---

## 14. PWA & Offline

### Service Worker

Angular Service Worker (`ngsw-worker.js`) enabled in production:
- **App shell** (HTML, CSS, JS) — prefetched on install.
- **Assets** (images, fonts) — lazy-cached on first use.
- **API calls** — network-first, **no caching** (`maxSize: 0`).

### Offline Detection

`AppComponent` checks `HEAD /api/public/settings` to detect offline state. Shows a full-screen splash with a retry button.

---

## 15. Testing

| Tool | Purpose |
|---|---|
| Vitest 4.0.17 | Test runner (Vite-based, fast) |
| @analogjs/vitest-angular | Angular test integration |
| @vitest/coverage-v8 | Code coverage |
| jsdom | DOM environment |

Test files: `*.spec.ts` alongside source files. Existing coverage: app component, auth initializer, services. Pattern follows standard Angular testing with mocked HTTP clients.

---

## 16. TypeScript Strictness

```jsonc
// tsconfig.json
{
  "strict": true,
  "noImplicitOverride": true,
  "noPropertyAccessFromIndexSignature": true,
  "noImplicitReturns": true,
  "noFallthroughCasesInSwitch": true,
  "target": "ES2022",
  // Angular-specific
  "strictInjectionParameters": true,
  "strictInputAccessModifiers": true,
  "strictTemplates": true
}
```

Full strictness is enforced — type safety across templates and injection.

---

## 17. Maintainability Assessment

### Strengths

- **Modern Angular** — standalone components, `inject()`, signals, Vite build.
- **Feature-based organization** — clear ownership boundaries.
- **Consistent patterns** — every service uses the same Observable/BehaviorSubject approach.
- **Performance-conscious** — virtual scrolling, lazy loading, lazy images, PWA caching.
- **Comprehensive i18n** — 18 languages with proper infrastructure.
- **Full TypeScript strictness** — catches errors at compile time.
- **Clean auth flow** — interceptor handles token refresh transparently.

### Areas to Watch

- **No central state management** — as complexity grows, distributed BehaviorSubjects can become hard to debug. Consider whether NgRx Signals or a lightweight store would help.
- **BookBrowserComponent complexity** — serves 5 different route contexts with lots of conditional logic. May benefit from splitting.
- **Test coverage** — only a few spec files exist. Critical services (BookService, AuthService) need tests.
- **Build budget is generous** — 6 MB error threshold. Monitor bundle size as features grow.
- **Component-level SCSS** — no shared design token system beyond PrimeNG's theming.

---

## 18. Backend Overview (for context)

The backend is a **Spring Boot 4.0.3** app (Java 25 with virtual threads).

### What you need to know as a frontend dev

- **API base**: All endpoints under `/api/v1/*`. Responses are JSON. Errors return `{ code, message, status }`.
- **Auth**: Backend issues JWTs on login. Access token expiry is short; refresh token is long-lived. The `/api/v1/auth/refresh` endpoint issues new pairs.
- **WebSocket**: STOMP broker at `/ws`. Backend pushes to `/user/queue/*` destinations — user-scoped. Auth via JWT in STOMP connect headers.
- **File streaming**: Covers, EPUB resources, PDF files, and custom fonts are served with **JWT as a query parameter** (`?token=...`), not in the Authorization header. This is because `<img>` tags and iframe sources can't set headers.
- **Permissions**: 19 boolean permission flags per user. Backend checks these on each request. Frontend should check them too (for UI hiding), but backend is the authority.
- **OPDS/Kobo/KOReader**: These are device-sync protocols the backend handles. The frontend doesn't interact with them directly — they have their own auth mechanisms.
- **Background tasks**: Long-running operations (library scan, metadata refresh) run as async tasks. Backend streams progress via WebSocket. Frontend shows progress bars and notifications.
- **Database**: MariaDB. Schema managed by Flyway migrations. 70+ entity tables. The schema is complex — if you need to understand data shapes, look at the DTOs in `booklore-api/src/main/java/org/booklore/model/dto/`.
- **Metadata providers**: Backend fetches metadata from 9 external sources (Google Books, GoodReads, Amazon, Hardcover, Comicvine, Douban, Lubimyczytac, Ranobedb, Audible). The UI shows proposals and lets users pick/merge.
- **Content types**: Backend handles PDF, EPUB, CBZ/CBR, MOBI, AZW3, FB2, audiobooks (M4B, MP3, Opus), and physical books (no file). Each has different metadata and progress models.

### Dev setup

Run `dev.docker-compose.yml` — it starts MariaDB on port 3366, backend on port 6060, and Angular dev server on port 4200. Backend accepts `ALLOWED_ORIGINS=http://localhost:4200` for CORS in dev mode.
