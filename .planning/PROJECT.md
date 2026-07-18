# BookTracker

## What This Is

A personal reading-tracker web app — your "Goodreads, done right." Users log books, track reading progress, rate and review, set a yearly reading goal, see personal analytics, follow other readers, send friend requests, and receive real-time notifications — all from a mobile-first installable PWA. Built as a portfolio-grade software engineering project targeting Entelect and BBD graduate roles.

## Core Value

A working app you actually use on your phone every day — plus the ability to confidently whiteboard and extend every layer of it in an interview.

## Current State

**Version:** v1.2 in progress — Phase 16 (Playwright E2E) complete; Phase 17 (Profile Photos, cuttable) next
**Stack:** Java 21 + Spring Boot 3.4 (Web, Data JPA, Security, Validation, Flyway) + React 18 + Vite + PostgreSQL 16
**Deployment:** Render (backend bundled-monolith Dockerfile; Render managed PostgreSQL)
**CI:** GitHub Actions (Testcontainers + Playwright E2E on push; Render deploy on main)
**Codebase:** ~13,600 Java LOC + ~7,000 TypeScript LOC + e2e/ Playwright suite (3 specs, 8 tests).
**DB schema:** 9 tables (users, books, user_books, friend_requests, goals, likes, notifications, reading_activity, follows-dropped-V9) + 9 Flyway migrations (V1–V9)

## Requirements

### Validated

- ✓ User registration and login (email/password, JWT auth, BCrypt) — v1.0
- ✓ Search books via Open Library API (proxied, paginated, defensive null handling) — v1.0
- ✓ Add book to shelf with status (WANT_TO_READ / CURRENTLY_READING / READ) — v1.0
- ✓ Cache book data locally on first fetch (cache-or-fetch strategy) — v1.0
- ✓ Update reading progress (current page, auto-finish date on READ) — v1.0
- ✓ Rate (1–5) and review books on shelf entries — v1.0
- ✓ Set and track a yearly reading goal — v1.0
- ✓ Stats/analytics (books read, genre breakdown, pages, goal progress, 12-month chart) — v1.0
- ✓ Mobile-first React PWA (installable, offline shell, NetworkFirst API caching) — v1.0
- ✓ Ownership enforcement: 403 (not 404) on cross-user shelf access — v1.0
- ✓ `docker-compose up` runs the full stack locally — v1.0
- ✓ GitHub Actions CI badge (Testcontainers + frontend build, Render deploy on main) — v1.0
- ✓ Live public URL on Render — v1.0
- ✓ Follow / unfollow other users (no self-follow → 400) — v1.0
- ✓ Public profiles: display name, goal progress, READ shelf with reviews — v1.0
- ✓ Activity feed: recent finishes and reviews from followed users — v1.0
- ✓ Friend requests: send/accept/reject/cancel; friends see each other's activity — v1.0
- ✓ User search by username / display name — v1.0
- ✓ Review likes (likeCount + likedByMe on public profiles) — v1.0
- ✓ Real-time STOMP/SockJS push notifications (friend request, accepted, book finished, review liked) — v1.0
- ✓ Notification bell with unread badge; inbox marks all as read — v1.0
- ✓ DNF shelf status (ABANDONED state, dedicated shelf tab, isolated from stats/social) — Phase 10, v1.1
- ✓ UI redesign with design system (amber accent tokens, dark mode with FOUC-free first paint incl. OS preference, dark-mode Recharts, 2:3 cover ratio, auth split layout) — Phase 11, v1.1
- ✓ Generated avatars (Dicebear, seeded from user UUID, rendered on all seven identity surfaces, offline-cached via SW CacheFirst) — Phase 12, v1.1
- ✓ Reading streaks + pace projections (current/longest streak on stats, estimated finish date on in-progress shelf cards, server-assigned activity dates) — Phase 12, v1.1

### Active

- Real-device mobile verification pass — live app tested on an actual phone (browser + installed PWA); layout/usability issues found there fixed
- Streak callout card — flame icon + motivational empty state on stats
- Richer stats — pace trends and genre-based projections
- ✓ Playwright E2E suite (scaffold + auth smoke + reading loop + auth boundary + CI job) — Phase 16, v1.2
- Profile photo uploads via external image host (cuttable if deadline pressure)
- Portfolio close-out — polished README, `.claude/` config tracked in repo, final v1.2 tag

## Current Milestone: v1.2 Mobile Shakedown & Close-out

**Goal:** Verify and polish the app on a real phone, clear the remaining backlog, and leave the repo interview-ready — final milestone before class starts (~2026-07-20).

**Target features:**
- Real-device mobile verification pass with fixes (discovery-driven — issues unknown until tested)
- Streak callout card (flame icon + motivational empty state)
- Richer stats: pace trends, genre-based projections
- Playwright E2E tests for core flows
- Profile photo uploads (external image host, e.g. Cloudinary) — last phase, explicitly cuttable
- Portfolio close-out: README with screenshots/architecture/setup, track `.claude/`, tag v1.2

**Hard constraint:** class starts ~2026-07-20 — phases ordered by portfolio value; milestone must be shippable even if the tail is cut.

### Out of Scope

- Goodreads CSV import — deferred to post-v2 (adds scope without affecting interview talking points)
- Normalized authors table — comma-joined varchar is sufficient for MVP
- OAuth / social login — email/password sufficient for the target audience
- Native mobile app — PWA covers the phone-use case
- Real-time DMs, barcode scanning, recommendation engine — full project surfaces, no portfolio payoff

## Context

- **Target employers:** Entelect and BBD — SA enterprise software consultancies (Java/.NET). They assess engineering fundamentals: clean relational modelling, proper auth, RESTful API design, tests, deployment.
- **Meta goal:** the repo is evidence. Being able to explain and extend every layer live in an interview is the actual deliverable. Every shortcut taken is a question you can't answer.
- **Timeline:** v1.0 MVP shipped 2026-07-06 (14 days from 2026-06-22 start); v1.1 Polish & Depth shipped 2026-07-11 (5 further days, 58 commits).
- **Dev environment:** Windows desktop (16 GB RAM), WSL2. JDK 21, Node, Docker Desktop + WSL2 integration all configured.
- **External dependency:** Open Library API (free, no key). `User-Agent` header set; data cached locally per their etiquette. Page count often absent — stats skip nulls gracefully.

## Constraints

- **Tech stack**: Java 21 + Spring Boot 3 (Web, Data JPA, Security, Validation, Flyway), React 18 + Vite, PostgreSQL 16 — locked.
- **Auth**: JWT + BCrypt. No OAuth for MVP.
- **Database**: PostgreSQL via Docker locally. UUID PKs throughout. Flyway for versioned migrations.
- **Deployment**: Render (decided at Phase 7 — simpler than Cloud Run + Netlify; sufficient for portfolio credibility at the interview stage).
- **ORM strategy**: `@Enumerated(EnumType.STRING)` for shelf_status — never ORDINAL. Flyway migrations are immutable once applied.
- **Performance**: paginate all list endpoints; index `(user_id, shelf_status)` and FK columns; JOIN FETCH to avoid N+1 on shelf listings.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Java + Spring Boot (not C#/.NET) | Builds on existing Java knowledge; Spring Boot mirrors the BBD/Entelect enterprise stack | ✓ Good — full stack built cleanly, no language friction |
| PostgreSQL over MySQL | Production-grade, enterprise-standard; Entelect/BBD run it | ✓ Good — UUID native type, clean Flyway migrations |
| Open Library over Google Books | Free, no API key; simpler setup | ✓ Good — no credential management, easy to explain |
| Cache-or-fetch book data locally | Respects Open Library etiquette; stats run as fast local SQL | ✓ Good — resilient to API downtime, clean separation |
| UUID PKs throughout | Avoids sequential ID enumeration (IDOR defense) | ✓ Good — consistent, industry standard |
| Social layer in same repo as MVP | Continuity of data model; follows/feeds build on same tables | ✓ Good — clean evolution from Phase 7 to 8 to 9 |
| 403 (not 404) for ownership violations | Signals engineering maturity; ready to defend the tradeoff | ✓ Good — tested in integration tests |
| Context-path removed at Phase 7 | SpaController catch-all requires /api prefix on controllers; PathPatternParser incompatibility with `/**` patterns | ✓ Good — 3-rule SecurityConfig clean and predictable |
| Bundled-monolith Dockerfile | Single container for Render; no separate static hosting needed | ✓ Good — simplified CI/CD and Render config |
| Render over Cloud Run + Netlify | Simpler for portfolio; no GCP setup needed | ✓ Good — shipped faster, still CV-impressive |
| STOMP over SockJS for WebSocket | Browser compat; SockJS polyfill available for Vite | ✓ Good — worked with JwtChannelInterceptor cleanly |
| cancelRequest deletes row (not CANCELLED status) | Allows requester to re-send after cancel | ✓ Good — simpler state machine |
| Friend feed via friend_requests (not follows) | Phase 9 moved feed from follow-based to friend-based | ✓ Good — consistent with friend request model |
| Inline FOUC bootstrap + native color-scheme layers | Dark first-paint must not depend on React mount or CSS-load timing | ✓ Good — flash-free on stored and OS-preference paths (UAT-verified) |
| Theme persisted only on explicit toggle | Storing the OS snapshot would permanently break the prefers-color-scheme fallback chain (UI-08) | ✓ Good — live OS theme changes tracked until user chooses |
| Avatar seed = immutable user UUID, rendered img-src only | Stable identity regardless of display-name changes; SVG scripts inert in image context (XSS-safe) | ✓ Good — consistent avatar on all seven surfaces, offline-cached |
| Activity dates always server-assigned `LocalDate.now()` | Client-supplied dates would allow streak forgery; `ON CONFLICT DO NOTHING` upsert makes same-day recording idempotent | ✓ Good — no request DTO carries a date; double-insert tested |
| Full QueryClient clear at auth boundaries (not user-scoped query keys) | One `clearAuthSession` helper at sign-out/401/sign-in is simpler than threading user IDs through every query key | ✓ Good — fixed cross-user cache leaks (UAT 3/4); regression suites pin it |
| E2E `workers:1` (not parallel) | Shared PostgreSQL E2E database — parallel workers cause shelf race conditions | ✓ Good — 8 tests deterministic at 14–24s |
| Dual webServer array in playwright.config.ts (mock on 9999, jar on 8080) | Guarantees mock starts before jar; SSRF guard disabled via `-Dopenlibrary.validate-base-url=false` | ✓ Good — Open Library stubs isolated; no third-party network dependency in E2E |
| auth.setup.ts as setup project (not globalSetup) | Traces from auth setup visible in HTML report; fixtures available for dependent specs | ✓ Good — debuggable auth failures |
| Pre-condition API cleanup in E2E specs (not test ordering) | Persistent E2E database across runs causes duplicate-entry failures without cleanup; test ordering is brittle | ✓ Good — all 8 tests pass on re-runs (idempotent) |
| `request` fixture uses explicit `Authorization: Bearer` header | Playwright `request` fixture does NOT inherit localStorage from `storageState` — must pass JWT explicitly | ✓ Good — prevents silent 401s in pre-condition cleanup |
| `E2E_JWT_SECRET` from GitHub Actions secrets | Secret must match the signing key used by the running Spring app; never hardcoded in YAML | ✓ Good — CI e2e job passes with repo secret set |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-07-18 after Phase 16 (playwright-e2e)*
