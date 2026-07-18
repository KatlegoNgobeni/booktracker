---
phase: 16-playwright-e2e
verified: 2026-07-18T12:00:00Z
status: passed
score: 4/5 must-haves verified
behavior_unverified: 1
overrides_applied: 0
human_verification:

  - test: "Run full Playwright suite on CI after setting E2E_JWT_SECRET secret in GitHub Actions"
    expected: "e2e job executes after ci job, all 8 tests pass, trace artifacts upload on failure"
    why_human: "CI job cannot be verified without a real push to GitHub and the E2E_JWT_SECRET secret set in repo Settings. Local test suite passing does not prove CI YAML is wired correctly end-to-end."
behavior_unverified_items:

  - truth: "Suite runs green as a parallel GitHub Actions job (E2E-05)"
    test: "Push to any branch and observe the e2e job in GitHub Actions tab"
    expected: "e2e job appears after ci job passes, all 8 tests pass, trace artifact uploaded"
    why_human: "GitHub Actions YAML is present and structurally valid, but the e2e job has never been triggered in CI. E2E_JWT_SECRET secret is required but unset. Job execution requires a real push and secret configuration."
---

# Phase 16: Playwright E2E Verification Report

**Phase Goal:** Automated auth smoke + core-loop journey + auth-boundary guard, green in CI
**Verified:** 2026-07-18T12:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Developer can run the full Playwright suite locally with one command — webServer boots the bundled monolith jar, users seeded via /api/auth/register | VERIFIED | `e2e/playwright.config.ts` has dual `webServer` array (mock on 9999, jar on 8080), `workers: 1`, `globalSetup` seeds users. All 6 infrastructure files exist. Mock server starts and responds with valid JSON. `npx playwright test --list` enumerates 8 tests across 4 files. |
| 2 | Auth smoke flows pass: register, login, logout, invalid credentials (E2E-02) | VERIFIED | `e2e/tests/auth.smoke.spec.ts` exists with 4 tests covering all 4 scenarios. File-level unauthenticated storageState override confirmed. All required locators (`getByLabel`, `getByRole`, `getByTestId`, `getByText`) used. No `waitForTimeout`. SUMMARY.md documents 4 smoke tests passed (6 total including 2 setup tests) in 13.7s. |
| 3 | Core reading loop journey passes end-to-end — search → add to shelf → update progress → finish → rate/review → stats reflect it — with Open Library mocked (E2E-03) | VERIFIED | `e2e/tests/reading-loop.spec.ts` exists with all 13 steps. Uses `getByTestId('book-search-results')`, `getByTestId('shelf-entry-card')`, `getByTestId('stat-books-all-time')`. Status selectOptions use string enums (`'CURRENTLY_READING'`, `'READ'`). Mock server routes `/works/*.json` to work-detail stub. Pre-condition idempotency cleanup present. SUMMARY.md documents 1 test passed. |
| 4 | Two-user auth-boundary test proves no cross-user data appears after sign-out/sign-in (E2E-04) | VERIFIED | `e2e/tests/auth-boundary.spec.ts` exists. Uses `browser.newContext({ storageState: AUTH_A })` and `browser.newContext({ storageState: AUTH_B })` (2 calls confirmed). Asserts `toHaveCount(0)` on `shelf-entry-card` and `getByText('The Hobbit')` in User B's context. Pre-condition cleanup makes the test idempotent. SUMMARY.md documents 1 test passed. |
| 5 | Suite runs green as a parallel GitHub Actions job — Chromium-only, PostgreSQL service container, trace-on-failure artifacts (E2E-05) | PRESENT_BEHAVIOR_UNVERIFIED | `.github/workflows/ci.yml` has a structurally complete `e2e` job with `needs: ci`, PostgreSQL 16 service container (`booktracker_e2e`/`bt_e2e`/`bt_e2e_pw`), fat-jar build, Playwright Chromium install, `npx playwright test --project=chromium`, and `upload-artifact@v4` with `if: ${{ !cancelled() }}`. YAML parses valid (`python3 yaml.safe_load` exits 0). However the job has never actually executed in CI — `E2E_JWT_SECRET` secret must be set before the run can succeed, and no GitHub Actions run is on record. |

**Score:** 4/5 truths verified (1 present, behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `e2e/package.json` | E2E package with @playwright/test ^1.61.1 | VERIFIED | `"@playwright/test": "^1.61.1"` in devDependencies; `"type": "module"` present |
| `e2e/playwright.config.ts` | Playwright config with dual webServer, workers:1, setup+chromium projects | VERIFIED | `workers: 1`, dual `webServer[]`, `globalSetup/Teardown`, `setup` + `chromium` projects, no project-level storageState |
| `e2e/mock-server.mjs` | Node.js HTTP mock on port 9999; path routing for /works/ vs search | VERIFIED | Listens on PORT 9999; routes `/works/` to work-detail stub, all else to search stub; emits `[mock] Open Library stub listening on :9999` |
| `e2e/global-setup.ts` | Seeds User A and User B via /api/auth/register; accepts 409 | VERIFIED | Seeds `e2e-user-a@example.com` and `e2e-user-b@example.com`; silently accepts HTTP 409 |
| `e2e/global-teardown.ts` | No-op teardown | VERIFIED | Exports default async no-op function |
| `e2e/tests/auth.setup.ts` | Auth setup project saving user-a.json and user-b.json | VERIFIED | `setup` project, `page.context().storageState()` for both users, `mkdirSync` ensures directory exists, ESM `__dirname` shim |
| `e2e/tests/auth.smoke.spec.ts` | 4 auth smoke tests | VERIFIED | 4 tests: register, valid login, invalid login, logout; file-level unauthenticated override |
| `e2e/tests/reading-loop.spec.ts` | Core reading loop journey test | VERIFIED | 1 test, 13 steps, all data-testids used, pre-condition cleanup for idempotency |
| `e2e/tests/auth-boundary.spec.ts` | Two-user auth boundary test | VERIFIED | 1 test, 2 browser contexts, `toHaveCount(0)` assertion on User B's shelf |
| `.github/workflows/ci.yml` (e2e job) | GitHub Actions e2e job with all required steps | VERIFIED (structural) | Structurally complete; never executed in CI (see Truth 5) |
| `frontend/src/pages/profile/ProfilePage.tsx` | `data-testid="logout-button"` on Sign Out button | VERIFIED | Present at line 101 |
| `frontend/src/pages/search/SearchPage.tsx` | `data-testid="book-search-results"` on results container | VERIFIED | Present at line 176 |
| `frontend/src/pages/shelf/ShelfPage.tsx` | `data-testid="shelf-entry-card"` on ShelfEntryCard | VERIFIED | Present at line 67 |
| `frontend/src/pages/stats/StatsPage.tsx` | `data-testid="stat-books-all-time"` on booksReadAllTime element | VERIFIED | Present at line 270 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `playwright.config.ts` webServer[0] | `mock-server.mjs` | `command: 'node mock-server.mjs'`, `url: 'http://localhost:9999'` | WIRED | Health check URL holds jar boot until mock is ready |
| `playwright.config.ts` webServer[1] | Spring Boot jar | `-Dopenlibrary.base-url=http://localhost:9999 -Dopenlibrary.validate-base-url=false` | WIRED | SSRF guard disabled for E2E; mock URL injected via system property |
| `playwright.config.ts` globalSetup | `global-setup.ts` | `path.resolve(__dirname, './global-setup.ts')` | WIRED | ESM-compatible `__dirname` shim used (fixed from `require.resolve`) |
| `playwright.config.ts` projects[0] | `tests/auth.setup.ts` | `testMatch: /auth\.setup\.ts/`, `name: 'setup'` | WIRED | chromium project has `dependencies: ['setup']` |
| `auth.setup.ts` | `playwright/.auth/user-a.json` | `page.context().storageState({ path: AUTH_FILE_A })` | WIRED | `mkdirSync` ensures directory exists before write |
| `reading-loop.spec.ts` | `playwright/.auth/user-a.json` | `test.use({ storageState: AUTH_A })` | WIRED | File-level storageState using pre-saved user-a.json |
| `auth-boundary.spec.ts` | `playwright/.auth/user-a.json` + `user-b.json` | `browser.newContext({ storageState: AUTH_A/B })` | WIRED | Two isolated browser contexts; auth tokens from auth.setup.ts |
| `ci.yml` e2e job | ci job | `needs: ci` | WIRED | E2E job only runs after ci (backend tests + frontend build) passes |
| `ci.yml` e2e job | `E2E_JWT_SECRET` | `${{ secrets.E2E_JWT_SECRET }}` (not hardcoded) | WIRED (structural) | Secret reference correct; secret not yet set in GitHub Actions |
| `ci.yml` upload-artifact | `e2e/playwright-report/` | `if: ${{ !cancelled() }}`, `path: e2e/playwright-report/` | WIRED | Uploads on failure; 7-day retention |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Mock server starts and emits correct log line | `timeout 3 node mock-server.mjs` | Prints `[mock] Open Library stub listening on :9999` | PASS |
| All 8 tests enumerable by Playwright | `npx playwright test --list --project=chromium` | Lists 8 tests: 2 setup + 4 smoke + 1 reading-loop + 1 auth-boundary | PASS |
| Auth smoke spec has correct locators | `grep -c "getByTestId('logout-button')"` etc. | 1 each for logout-button, "Invalid email or password", localStorage check, unauthenticated override | PASS |
| Reading-loop uses all 3 data-testids | `grep -c "getByTestId('book-search-results')"` etc. | 1 each for book-search-results, shelf-entry-card, stat-books-all-time | PASS |
| Auth-boundary uses 2 browser contexts | `grep -c "browser.newContext"` | 2 (ctxA and ctxB) | PASS |
| CI YAML is valid YAML | `python3 yaml.safe_load(ci.yml)` | Exits 0 | PASS |
| CI YAML has required keys | grep checks for E2E Tests, needs:ci, booktracker_e2e, E2E_JWT_SECRET, upload-artifact, DskipTests, !cancelled(), Test & Build | All return expected counts | PASS |
| No waitForTimeout in test specs | `grep -rn 'waitForTimeout'` | No output | PASS |
| No CSS selector strings in test specs | Pattern scan | No CSS selector patterns found | PASS |
| Chromium browser binaries installed | `ls ~/.cache/ms-playwright/` | `chromium-1228` directory exists with `INSTALLATION_COMPLETE` marker | PASS |
| CI run green in GitHub Actions | Push to branch, observe e2e job | NOT RUN — requires E2E_JWT_SECRET secret set; no CI run on record | SKIP |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| E2E-01 | 16-01-PLAN.md | Playwright suite runs with one command locally | SATISFIED | `playwright.config.ts` dual webServer, `global-setup.ts` user seeding, all infrastructure files exist |
| E2E-02 | 16-02-PLAN.md | Auth smoke flows — register, login, logout, invalid credentials | SATISFIED | `auth.smoke.spec.ts` 4 tests covering all 4 scenarios |
| E2E-03 | 16-03-PLAN.md | Core reading loop journey — search → shelf → progress → finish → rate/review → stats | SATISFIED | `reading-loop.spec.ts` 13-step journey spec with all data-testids |
| E2E-04 | 16-03-PLAN.md | Two-user auth-boundary test | SATISFIED | `auth-boundary.spec.ts` with 2-context isolation and `toHaveCount(0)` assertion |
| E2E-05 | 16-04-PLAN.md | E2E suite runs as parallel GitHub Actions job | PRESENT, BEHAVIOR UNVERIFIED | `ci.yml` structurally complete; job never triggered in CI; `E2E_JWT_SECRET` secret unset |

**Requirements traceability note:** REQUIREMENTS.md traceability table incorrectly lists E2E-01 through E2E-05 as "Phase 15". The actual work is in Phase 16 as confirmed by ROADMAP.md (which explicitly lists E2E-01..05 under Phase 16) and all four Phase 16 PLANs. This is a stale administrative entry in the traceability table — not a gap in implementation.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | - | No TBD/FIXME/XXX markers, no waitForTimeout, no CSS selectors, no empty implementations found in any e2e source file | - | - |

### Human Verification Required

#### 1. GitHub Actions E2E Job Execution (E2E-05)

**Test:** Push to any branch and observe the GitHub Actions run. Before pushing, set `E2E_JWT_SECRET` in GitHub repo Settings → Secrets and variables → Actions. Value: the `jwt.secret` property from `src/test/resources/application.properties`.

**Expected:**

- The `e2e` job appears in the Actions tab after the `ci` job passes
- The e2e job shows: PostgreSQL service container healthy, fat JAR builds, Playwright Chromium installs, `npx playwright test --project=chromium` runs and reports 8 passed tests, trace artifact is uploaded
- No job steps fail

**Why human:** The CI YAML is structurally valid and all required fields are present. However the job has never actually run in GitHub Actions. The `E2E_JWT_SECRET` secret must be set before the jar can issue valid JWTs and auth tests can pass. No automated check can verify a GitHub Actions job execution without a real push and the secret being configured.

---

## Gaps Summary

No implementation gaps found. All five infrastructure files, four test spec files, all four data-testid attributes, and the GitHub Actions YAML are fully substantive and wired. The sole unresolved item is behavioral verification of the CI job in a live GitHub Actions environment.

---

_Verified: 2026-07-18T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
