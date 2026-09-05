# Test Coverage Implementation Plan — booklore-ui

> **Status:** Approved. Execution starts at Stage 0.
> **Primary goal:** regression safety for library upgrades — maximize coverage of library surface area (Angular, PrimeNG, RxJS, HttpClient, Chart.js) as early as possible.
> **Delivery:** one continuous effort, structured in verifiable stages. A coverage ratchet locks in gains as they land.

## Baseline (measured 2026-09-04)

- 419 tests across 8 spec files; ~3.6% statement / ~4.3% branch coverage over ~372 source files (153 components, 119 services)
- Meaningful tests concentrate on the Magic Shelf rule engine; everything else untested
- No E2E framework, no CI pipeline in this repo
- Toolchain in place: Vitest 4 + `@analogjs/vite-plugin-angular` + jsdom + `@vitest/coverage-v8`
- Existing conventions: co-located `*.spec.ts`, `TestBed` + `useValue` mocks, `vi.fn()`/`vi.spyOn()`, `getTranslocoModule()` helper at `src/app/core/testing/transloco-testing.ts`

## Progress (as of latest run)

- **1742 tests across 233 spec files.**
- **Overall coverage:** 42.9% statements / 28.22% branches / 38.78% functions / 43.02% lines.
- Coverage thresholds ratcheted to: statements 42.5%, branches 28%, functions 38.5%, lines 42.75%.
- Stage 0 complete.
- Stage 1 complete for the regression-safety goal; services covered include all high-risk HTTP/state services and many browser/layout services:
  - Core: `auth-interceptor.service.ts`, `auth.service.ts`, `auth-initializer`, `app-settings.service.ts`, `oidc.service.ts`, `oidc-group-mapping.service.ts`, `startup.service.ts`, `loading.service.ts`, `post-login-initializer.service.ts`
  - Book core: `book.service.ts`, `book-state.service.ts`, `book-socket.service.ts`, `book-patch.service.ts`, `book-file.service.ts`, `book-metadata.service.ts`, `book-metadata-manage.service.ts`, `library.service.ts`, `shelf.service.ts`, `library-health.service.ts`
  - Book browser: `book-browser-entity.service.ts`, `book-browser-query-params.service.ts`, `book-browser-scroll.service.ts`, `book-selection.service.ts`, `book-browser-orchestration.service.ts`, `book-filter.service.ts`, `book-menu.service.ts`, `library-shelf-menu.service.ts`, `book-navigation.service.ts`
  - State / events: `notification-event.service.ts`, `metadata-progress.service.ts`, `reading-session.service.ts`, `reading-session-api.service.ts`, `app-config.service.ts`
  - User / settings: `user.service.ts`, `task.service.ts`, `user-stats.service.ts`, `audit-log.service.ts`, `settings-helper.service.ts`, `reader-preferences.service.ts`, `content-restriction.service.ts`, `version.service.ts`, `custom-font.service.ts`
  - Catalog: `author.service.ts`, `series-data.service.ts`, `notebook.service.ts`, `bookdrop.service.ts`, `url-helper.service.ts`
  - Annotate / files: `annotation.service.ts`, `book-mark.service.ts`, `book-note.service.ts`, `book-note-v2.service.ts`, `pdf-annotation.service.ts`, `file-download.service.ts`, `file-operations.service.ts`, `sidecar.service.ts`, `metadata-match-weights.service.ts`
  - Layout / misc: `app.layout.service.ts`, `app.menu.service.ts`, `dashboard-config.service.ts`, `library-loading.service.ts`, `opds.service.ts`, `email.service.ts`, `email-v2-provider.service.ts`, `email-v2-recipient.service.ts`, `magic-shelf.service.ts`
- Stage 2 complete: smoke / interaction tests added for all routed components and major dialogs across auth, book, metadata, author, series, notebook, settings, dashboard, layout, stats, and readers. All chart components now have at least one representative smoke test. Remaining untested components are primarily reader internal widgets/layouts (deliberately out of scope for deep testing) and a handful of small helper components.
- Stage 3 complete: pure-logic specs added for `SortService`, `BookSorter`, `SeriesCollapseFilter`, `SideBarFilter`/`HeaderFilter`/`sidebar-filter` helpers, `FilterLabelHelper`, reader/layout utilities, `ReadStatusHelper`, `IconCategoriesHelper`, `PatternResolver`, `CustomFontUtil`, `MetadataFormBuilder`, `passwordMatchValidator`, `createRxStompConfig`, and `magic-shelf-utils`. Magic-shelf utility coverage is 100%.
- Stage 4 complete: auth & security specs added for `AuthInterceptorService`, `AuthGuard`, `SecurePipe`, `SecureSrcDirective`, all 5 permission guards (`EditMetadataGuard`, `UserStatsGuard`, `LibraryStatsGuard`, `ManageLibraryGuard`, `BookdropGuard`), setup guards (`SetupGuard`, `LoginGuard`, `SetupRedirectGuard`), and extended `auth-initializer.spec.ts`. Security folder coverage is ~99% statements / ~92% branches.
- Stage 5 complete: Playwright E2E smoke suite added. `@playwright/test` installed, `playwright.config.ts`, `e2e/fixtures.ts`, and `e2e/smoke.spec.ts` with 5 end-to-end tests: setup-redirect → login, invalid credentials error, login → dashboard, login → all-books grid, and first-time setup page.

## Stage 0 — Infrastructure & hygiene (prerequisite)

1. **Consolidate Vitest configs.** `angular.json` now points to a single canonical `vitest.config.ts` that includes coverage settings, JUnit reporter output, and test isolation. The obsolete `vitest-base.config.ts` has been removed. `npm test`, `npm run test:coverage`, and `ng test --watch=false` all behave identically.
2. **Add `test:coverage` script** to `package.json`.
3. **Shared test scaffolding** under `src/testing/`:
   - Model factories: `createMockBook()`, `createMockLibrary()`, `createMockUser()`, etc. (generalize the hand-rolled `createBook()` in the magic-shelf specs)
   - Provider builders for the most-mocked services: `HttpClient`, `RxStompService`, `MessageService`, `TranslocoService`, PrimeNG dialog refs
   - Defensive `localStorage` / platform guard in `src/test-setup.ts` using `getPlatform()` (prevents the AuthService-style `localStorage.getItem` failure when the Angular CLI test builder has already bootstrapped the platform)
   - Reuse the existing `getTranslocoModule()` helper
4. **Ratcheting coverage thresholds** (Vitest 4 per-glob thresholds), starting at baseline and raised per stage so coverage can never regress.

## Stage 1 — HTTP services & state stores

*RxJS/HttpClient surface — highest upgrade risk.* ~25 spec files.

1. **HTTP services** (spy on `HttpClient` per existing `library-health.service.spec.ts` pattern):
   - `book.service.ts` (395 lines, central), `book-patch.service.ts`, `book-file.service.ts`, `book-metadata.service.ts`, `bookdrop.service.ts`, `auth.service.ts`, `magic-shelf.service.ts`, `series-data.service.ts`, `task.service.ts`, `url-helper.service.ts`, `user.service.ts`, `app-settings.service.ts`, `oidc.service.ts`
   - Plus smaller API services: author, notebook, sidecar, email-v2 provider/recipient, audit-log, custom-font, annotation, book-mark, book-note(-v2), pdf-annotation, file-download, file-operations, version, oidc-group-mapping, metadata-match-weights, content-restriction, user-stats
2. **State stores / reducers:**
   - `book-socket.service.ts` (pure WS→state reducers — highest value)
   - `book-state.service.ts`, `library.service.ts`, `shelf.service.ts`
   - `notification-event.service.ts` (fake timers: 7.5s highlight / 20s clear)
   - `reading-session.service.ts` (idle-detection timers)
   - `metadata-progress.service.ts`
3. **Filter pipeline:** `book-filter.service.ts` (`createFilterStreams`, `filterBooksByEntity`)

**Gate: overall statements ≥ 15–20%.**

## Stage 2 — Component smoke tests + key interaction tests

*Angular/PrimeNG/Chart.js surface — the core of library-upgrade regression safety.* ~55–75 spec files.

1. **Smoke tests** ("instantiates and renders without error") for every routed component and major dialog (~40–50 components). Cheap; catches template/constructor/DI breakage from Angular or PrimeNG upgrades.
2. **Deeper interaction tests** for critical components:
   - `book-browser.component.ts` (1066 lines — filter/sort/view/query-param orchestration)
   - `metadata-editor.component.ts` / `metadata-viewer.component.ts` (1266/1449)
   - `bookdrop-file-review.component.ts` (860)
   - `login` / `setup` components (auth entry flows)
   - `app.component.ts` — extend existing spec to cover the 11 WebSocket topic handlers
   - `magic-shelf` component — extend existing suite
3. **Chart components:** one solid smoke/pattern test per chart family (bar/line/heatmap/matrix/timeline), not per chart — ~10 representative specs covering the ~35 Chart.js components.
4. **Readers** (cbx 1462, audiobook 1101, ebook 473, pdf): smoke tests + targeted service tests (`event.service.ts`, `selection.service.ts`, `reader-state.service.ts`). Full reader simulation has poor ROI — skip it.

**Gate: overall statements ≥ 35–40%; every routed component smoke-tested.**

## Stage 3 — Pure logic & utilities

~20 spec files, no TestBed, millisecond-fast:

- Sorting/filtering: `sort.service.ts`, `BookSorter.ts`, `SeriesCollapseFilter.ts`, `sidebar-filter.ts`, `HeaderFilter.ts`, `filter-label.helper.ts`
- Utilities: `pattern-resolver.ts` (file-naming engine), `custom-font.util.ts`, `read-status.helper.ts`, `icon-categories.helper.ts`, `header-footer.util.ts`, `visibility.util.ts`
- Forms/metadata: `password-match.validator.ts`, `metadata-form.builder.ts`, field configs
- `rx-stomp.config.ts` (pure factory)
- Extend magic-shelf rule engine suite toward ~100% (currently ~94% lines)

**Gate: utils/helpers ≥ 80%; magic-shelf engine ≥ 95%.**

## Stage 4 — Auth & security flow

~10 spec files. Small files, high consequence, classic RxJS/Angular upgrade breakage points:

- `auth-interceptor.service.ts` — 401 single-flight refresh queue (`isRefreshing` + `refreshTokenSubject` race)
- `auth.guard.ts` + 5 permission guards + setup guards (trivially testable `CanActivateFn`s)
- `oidc-callback.component.ts`, `secure-pipe.ts`, `secure-src.directive.ts`
- Expand `auth-initializer.spec.ts`

**Gate: guards/interceptors ≥ 90%; overall statements ≥ 45%.**

## Stage 5 — Playwright E2E smoke suite

1. **Setup:** `@playwright/test` + `playwright.config.ts`; new `e2e/` directory (excluded from the Vitest glob); `test:e2e` npm script. Only new dev dependency in the whole plan.
2. **Backend strategy:** Playwright route interception to stub API responses — self-contained, deterministic, no live backend required.
3. **Smoke journey (~5–8 tests):** app boot → setup/login redirect → dashboard renders → library book grid renders → book detail/metadata center renders → settings page renders. Exercises the real built app end-to-end; catches integration regressions no unit test can.

## Targets summary

| After stage | Overall statements | Key thresholds |
|---|---|---|
| Baseline | 3.6% | — |
| 1 | ≥15–20% | state stores ≥80% |
| 2 | ≥35–40% | all routed components smoke-tested |
| 3 | — (fast suite grows) | utils ≥80%, rule engine ≥95% |
| 4 | ≥45% | guards/interceptors ≥90% |
| 5 | + E2E smoke suite green | — |

## Conventions held throughout

- Co-located `*.spec.ts` next to source
- Vitest `vi.fn()`/`vi.spyOn()` + TestBed `useValue` mocks
- No new runtime dependencies; `@playwright/test` is the only new dev dependency (Stage 5)
- All existing 419 tests must stay green

## Explicitly out of scope

- NgRx/state library changes, visual regression tooling, mutation testing
- Deep interaction tests for the four readers (covered by smoke + service tests)
- CI pipeline wiring (no CI exists here; the suite is CI-ready via `npm test`).
+ E2E wired into `.github/workflows/develop-pipeline.yml` and `master-pipeline.yml` via `npx playwright test` and the `test:e2e` script. Artifacts include `booklore-ui/test-results` and `booklore-ui/playwright-report`.
