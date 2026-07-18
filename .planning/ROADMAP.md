# Roadmap: BookTracker

## Milestones

- ✅ **v1.0 MVP** — Phases 1–9 (shipped 2026-07-06)
- ✅ **v1.1 Polish & Depth** — Phases 10–12 (shipped 2026-07-11)
- 🚧 **v1.2 Mobile Shakedown & Close-out** — Phases 13–17 (in progress, hard deadline ~2026-07-20)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1–9) — SHIPPED 2026-07-06</summary>

- [x] Phase 1: Foundation (2/2 plans) — completed 2026-06-23
- [x] Phase 2: Authentication (2/2 plans) — completed 2026-06-23
- [x] Phase 3: Books & Open Library (2/2 plans) — completed 2026-06-24
- [x] Phase 4: Shelf CRUD (2/2 plans) — completed 2026-06-25
- [x] Phase 5: Stats & Goal (2/2 plans) — completed 2026-06-25
- [x] Phase 6: React Frontend & PWA (5/5 plans) — completed 2026-06-26
- [x] Phase 7: Tests, CI & Deploy (3/3 plans) — completed 2026-06-29
- [x] Phase 8: Social Layer (2/2 plans) — completed 2026-07-02
- [x] Phase 9: User Discovery, Friend Requests & Real-time Notifications (6/6 plans) — completed 2026-07-05

Full phase details: [.planning/milestones/v1.0-ROADMAP.md](.planning/milestones/v1.0-ROADMAP.md)

</details>

<details>
<summary>✅ v1.1 Polish & Depth (Phases 10–12) — SHIPPED 2026-07-11</summary>

- [x] Phase 10: DNF Shelf Status (3/3 plans) — completed 2026-07-06
- [x] Phase 11: UI Design System (3/3 plans) — completed 2026-07-07
- [x] Phase 12: Generated Avatars, Reading Streaks & Pace Projections (8/8 plans) — completed 2026-07-10

Full phase details: [.planning/milestones/v1.1-ROADMAP.md](.planning/milestones/v1.1-ROADMAP.md)

</details>

### v1.2 Mobile Shakedown & Close-out (In Progress)

**Milestone goal:** Verify and polish the app on a real phone, clear the remaining backlog, and leave the repo interview-ready — final milestone before class starts (~2026-07-20).

**Cut order under deadline pressure:** Phase 17 (Profile Photos) first, then STATS-07/STATS-08 within Phase 14. Every cut line leaves a shippable milestone. Phase 18 (Close-out) is strictly last and never cut.

- [x] **Phase 13: Mobile Shakedown** - Real-device verification pass with triaged fixes; core reading loop works on an actual phone in both browser and installed PWA modes (completed 2026-07-14)
  - Plans: 4 plans
  - [x] 13-01-PLAN.md — MOB-01: build version string end-to-end (Vite define + Dockerfile ARG + UI display)
  - [x] 13-02-PLAN.md — MOB-02/03/04/06: global iOS fixes (safe areas, dvh, input zoom, SW update registration)
  - [x] 13-03-PLAN.md — MOB-05: on-device discovery pass with triage gate (broken/degraded/nit)
  - [x] 13-04-PLAN.md — MOB-05/06: fix broken items + MOB-06 SW self-update on-device verification
- [x] **Phase 14: Stats Enrichment** - Streak callout card, pace trends, ahead/behind verdict; genre projections as sub-cuttable tail (completed 2026-07-15)
- [x] **Phase 15: Social Discovery & Recommendations** - Letterboxd-style Feed tab with friends activity, trending books, follower/following count bug fix (completed 2026-07-16)
- [x] **Phase 16: Playwright E2E** - Automated auth smoke + core-loop journey + auth-boundary guard, green in CI (4 plans) (completed 2026-07-18)
- [ ] **Phase 17: Profile Photos** - Cloudinary-backed photo uploads replacing generated avatars (CUTTABLE — cut first under deadline pressure)
- [ ] **Phase 18: Portfolio Close-out** - Interview-ready README, secret-scanned history, clean-clone verification, v1.2 tag (strictly last)

## Phase Details

### Phase 13: Mobile Shakedown

**Goal**: The core reading loop works flawlessly on a real phone — in both browser-tab and installed-PWA modes — with device-vs-deploy staleness diagnosable at a glance
**Depends on**: Nothing (first phase of milestone; builds on shipped v1.1)
**Requirements**: MOB-01, MOB-02, MOB-03, MOB-04, MOB-05, MOB-06
**Success Criteria** (what must be TRUE):

  1. User can see the running build version in the UI on any device, so a stale service worker is diagnosable before any fix is tested (MOB-01 lands as the FIRST commit of the phase)
  2. Layout respects device safe areas (notch, home indicator) and uses full dynamic viewport height in both browser-tab and installed standalone modes — no content hidden behind mobile browser chrome or the BottomNav
  3. Tapping any form input on iOS does not trigger auto-zoom
  4. User can complete the core reading loop (search → add → progress → finish → rate/review → stats) on a real phone without layout breakage — device findings triaged broken/degraded/nit, all "broken" fixed, "nit" explicitly deferred
  5. Installed PWA picks up a new deployment without manual cache clearing, verified on-device

**Plans**: 4/4 plans complete
**UI hint**: yes

### Phase 14: Stats Enrichment

**Goal**: The stats page tells the user how their reading year is actually going — streak health, pace versus goal, and (if data permits) where the year is projected to land
**Depends on**: Phase 13 (soft — new stats UI lands on the corrected mobile viewport)
**Requirements**: STATS-01, STATS-02, STATS-03, STATS-04, STATS-05, STATS-06, STATS-07 *(sub-cuttable)*, STATS-08 *(sub-cuttable)*
**Success Criteria** (what must be TRUE):

  1. User sees a streak callout card on the stats page — flame icon, current streak count, streak-health styling
  2. User with no active streak sees a motivational empty state, showing their longest-streak record when one exists
  3. User sees a monthly pace trend chart with an on-track reference line, plus an ahead/behind-schedule verdict ("N books ahead/behind") and a pages-per-day target on the goal progress card
  4. Fresh and sparse accounts see graceful gaps and minimum-data empty states in pace charts — never zero-lines (what an interviewer's fresh demo account sees)
  5. *(sub-cuttable)* Books capture Open Library subject data on fetch (additive V8+ migration) and user sees a genre-weighted year-end projection built on it — cutting STATS-07/08 leaves criteria 1–4 fully shippable

**Plans**: 2/2 plans complete
Plans:

- [x] 14-01-PLAN.md — STATS-01/02/03/04/05/06: streak callout card, empty state, pace reference line, ahead/behind verdict, pages-per-day, fresh-account chart empty state (pure frontend)
- [x] 14-02-PLAN.md — STATS-07/08: V8 subjects migration, BookEntity/OpenLibraryWorkResponse/BookService subjects capture, topGenre stat card *(sub-cuttable)*

**UI hint**: yes

### Phase 15: Social Discovery & Recommendations

**Goal**: The Feed tab becomes the app's home screen — a Letterboxd-style discovery surface showing what friends are reading, curated trending books, and personalised picks, plus a bug fix making follower/following counts update live when social connections change
**Depends on**: Phase 13 (mobile-correct layout), Phase 8 (social graph already built — friends, followers, following all exist)
**Requirements**: DISC-01, DISC-02, DISC-03, DISC-04, DISC-05
**Success Criteria** (what must be TRUE):

  1. Feed tab opens to a discovery screen (not the raw activity feed) — sections: "Friends are reading", "Trending this week" (Open Library subjects API or curated list), and optionally "Your picks" based on shelf genres
  2. Each book card in the discovery section is tappable — goes to a book detail / add-to-shelf flow
  3. Follower and following counts on a user's profile update immediately after follow/unfollow without requiring a page reload
  4. The raw activity feed (what friends have shelved/finished recently) is still reachable — either as a tab within Feed or a section below the discovery content
  5. Works correctly on mobile (iPhone Safari) — discovery cards are touch-friendly, no layout overflow

**Plans**: 4/4 plans complete

Plans:

- [x] 15-01-PLAN.md — Wave 1: test scaffolds for DISC-01/02/03/04 (FeedPage.test.tsx, useSocial.test.ts, SocialIntegrationTest extension)
- [x] 15-02-PLAN.md — Wave 1: new GET /api/feed/friends-reading endpoint + FriendsReadingItemDto + dead-code removal
- [x] 15-03-PLAN.md — Wave 2: FriendsReadingItem type, friendsReading query key, useFriendsReading hook, follow-count bug fix
- [x] 15-04-PLAN.md — Wave 3: DiscoverySection + DiscoveryBookCard components, trending.ts, FeedPage restructure

**UI hint**: yes

### Phase 16: Playwright E2E

**Goal**: The core product journeys are pinned by an automated E2E suite that runs green in CI and guards every deployment after it
**Depends on**: Phase 13 (stable post-shakedown UI to assert against; role-based locators tolerate restyle)
**Requirements**: E2E-01, E2E-02, E2E-03, E2E-04, E2E-05
**Success Criteria** (what must be TRUE):

  1. Developer can run the full Playwright suite locally with one command — `webServer` boots the bundled monolith jar, users seeded via `/api/auth/register` (never SQL fixtures)
  2. Auth smoke flows pass: register, login, logout, invalid credentials
  3. Core reading loop journey passes end-to-end — search → add to shelf → update progress → finish → rate/review → stats reflect it — with Open Library mocked, never hit live in CI
  4. Two-user auth-boundary test proves no cross-user data appears after sign-out/sign-in (pins the v1.1 cache-leak fix)
  5. Suite runs green as a parallel GitHub Actions job — Chromium-only, PostgreSQL service container, trace-on-failure artifacts

**Plans**: 4/4 plans complete

Plans:

- [x] 16-01-PLAN.md — E2E infrastructure: Playwright config, mock server, global setup/teardown, auth setup project, 4 data-testid additions (E2E-01)
- [x] 16-02-PLAN.md — Auth smoke spec: register, login valid, login invalid, logout (E2E-02)
- [x] 16-03-PLAN.md — Journey specs: reading loop (E2E-03) + two-user auth boundary (E2E-04)
- [x] 16-04-PLAN.md — GitHub Actions e2e job with PostgreSQL service container (E2E-05)

### Phase 17: Profile Photos

**Goal**: Users can put their real face on their profile — uploaded from their phone, hosted externally, replacing the generated avatar everywhere it appears
**Depends on**: Phase 16 (ordering only — pre-agreed CUTTABLE phase, deliberately second-to-last; nothing downstream depends on it except close-out screenshots)
**Requirements**: PHOTO-01, PHOTO-02, PHOTO-03, PHOTO-04
**Success Criteria** (what must be TRUE):

  1. User can upload a profile photo from their device via a simple file picker; Cloudinary face-centered auto-crop applies without any crop UI
  2. Uploaded photo replaces the generated avatar on all seven identity surfaces; users without photos keep the Dicebear fallback
  3. Oversize (>5MB) or non-image uploads are rejected with clear errors — validation is server-side (magic bytes), the endpoint is JWT-authenticated, and no Cloudinary credentials or presets are exposed to the client
  4. User can remove their photo and revert to the generated avatar

**Plans**: TBD
**UI hint**: yes

### Phase 18: Portfolio Close-out

**Goal**: A stranger (interviewer) landing on the repo understands the project in 30 seconds, can run it in minutes, and everything they see matches the live shipped app
**Depends on**: Phase 13, Phase 14, Phase 15, Phase 16, Phase 17 (or its cut decision) — strictly last: screenshots capture the final UI, the tag marks a verified deploy
**Requirements**: DOCS-01, DOCS-02, DOCS-03, DOCS-04, DOCS-05
**Success Criteria** (what must be TRUE):

  1. Repo visitor sees a README leading with a hero screenshot, a live demo link with demo credentials, and a quick-start that works exactly as written
  2. README includes Mermaid architecture + ER diagrams and an engineering-highlights section (403-vs-404, server-assigned streak dates, FOUC-free dark mode, auth-boundary teardown)
  3. Full git history passes a secret scan BEFORE `.claude/` project config is tracked in the repo
  4. A clean clone runs with `docker-compose up`, verified end-to-end
  5. Final `v1.2` tag is pushed against a verified live Render deploy (ordering: scan → clone test → screenshots → tag)

**Plans**: TBD

## Progress

**Execution order (v1.2):** 13 → 14 → 15 → 16 → 17 (cuttable) → 18 (strictly last)

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation | v1.0 | 2/2 | Complete | 2026-06-23 |
| 2. Authentication | v1.0 | 2/2 | Complete | 2026-06-23 |
| 3. Books & Open Library | v1.0 | 2/2 | Complete | 2026-06-24 |
| 4. Shelf CRUD | v1.0 | 2/2 | Complete | 2026-06-25 |
| 5. Stats & Goal | v1.0 | 2/2 | Complete | 2026-06-25 |
| 6. React Frontend & PWA | v1.0 | 5/5 | Complete | 2026-06-26 |
| 7. Tests, CI & Deploy | v1.0 | 3/3 | Complete | 2026-06-29 |
| 8. Social Layer | v1.0 | 2/2 | Complete | 2026-07-02 |
| 9. User Discovery & Notifications | v1.0 | 6/6 | Complete | 2026-07-05 |
| 10. DNF Shelf Status | v1.1 | 3/3 | Complete | 2026-07-06 |
| 11. UI Design System | v1.1 | 3/3 | Complete | 2026-07-07 |
| 12. Generated Avatars, Reading Streaks & Pace Projections | v1.1 | 8/8 | Complete | 2026-07-10 |
| 13. Mobile Shakedown | v1.2 | 4/4 | Complete   | 2026-07-14 |
| 14. Stats Enrichment | v1.2 | 2/2 | Complete   | 2026-07-15 |
| 15. Social Discovery & Recommendations | v1.2 | 4/4 | Complete   | 2026-07-16 |
| 16. Playwright E2E | v1.2 | 4/4 | Complete    | 2026-07-18 |
| 17. Profile Photos | v1.2 | 0/? | Not started (CUTTABLE) | - |
| 18. Portfolio Close-out | v1.2 | 0/? | Not started | - |
