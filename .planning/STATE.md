---
gsd_state_version: 1.0
milestone: v1.3
milestone_name: Book Collections
current_phase: 18
current_phase_name: portfolio-close-out
status: planned
stopped_at: context exhaustion at 76% (2026-07-22)
last_updated: "2026-07-22T16:34:11.482Z"
last_activity: 2026-07-20
last_activity_desc: "Phase 18 Plan 01: interview-ready README + secret scan"
progress:
  total_phases: 7
  completed_phases: 6
  total_plans: 20
  completed_plans: 18
  percent: 86
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-13)

**Core value:** A working reading-tracker you actually use on your phone, plus the ability to confidently whiteboard and extend every layer in an Entelect/BBD interview.
**Current focus:** Phase 18 — portfolio-close-out

## Current Position

Phase: 18 (portfolio-close-out) — EXECUTING
Plan: 1 of 1 — COMPLETE
Status: Phase 18 Plan 01 complete — awaiting v1.2 tag
Last activity: 2026-07-20 — Phase 18 Plan 01: interview-ready README + secret scan

Progress: [████████████████████] 14/14 plans (100%)

## Milestone Constraints

- **Hard deadline:** class starts ~2026-07-20 — milestone must be shippable at every cut line
- **Cut order:** Phase 16 (Profile Photos) first, then STATS-07/STATS-08 (genre capture + projection) within Phase 14
- **Phase 17 (Close-out) is strictly last** — screenshots the final UI, tags a verified deploy; never cut
- **MOB-01 (version string) is the FIRST commit of Phase 13** — before any mobile fix, or stale service workers mask fixes on-device

## Milestone Archive

- v1.0 MVP — shipped 2026-07-06
  - 9 phases, 26 plans, 120 commits, 14 days
  - Archive: .planning/milestones/v1.0-ROADMAP.md
  - Requirements: .planning/milestones/v1.0-REQUIREMENTS.md
  - Tag: v1.0
- v1.1 Polish & Depth — shipped 2026-07-11
  - 3 phases, 14 plans, 58 commits, 5 days
  - Archive: .planning/milestones/v1.1-ROADMAP.md
  - Requirements: .planning/milestones/v1.1-REQUIREMENTS.md
  - Tag: v1.1

## Accumulated Context

### Decisions

All milestone decisions logged in PROJECT.md Key Decisions table.

Key architecture decisions that carry forward:

- Java 21 + Spring Boot 3 + React 18 + Vite + PostgreSQL 16 — locked tech stack
- UUID PKs throughout; @Enumerated(EnumType.STRING); Flyway owns schema exclusively
- 403 (not 404) for ownership violations
- Context-path removed at Phase 7; /api prefix on all controllers; 3-rule SecurityConfig
- Bundled-monolith Dockerfile; Render deployment
- STOMP over SockJS for WebSocket; JwtChannelInterceptor for auth
- ABANDONED as fourth ShelfStatus constant; 3-location enum sync (Java + TypeScript + Zod)
- Flyway V1–V7 are immutable (live in production). New schema changes go in V8+ only.
- reading_activity uses composite natural PK (user_id, activity_date) — documented exception to UUID-PK convention
- Activity dates always server-assigned LocalDate.now(); only updateProgress stamps lastReadDate (pace anchor)
- Avatar seed = immutable user UUID; Dicebear rendered img-src only; offline-cached via SW CacheFirst
- Three-layer native color-scheme defense for FOUC; theme persisted only on explicit toggle
- clearAuthSession(queryClient) teardown at all three auth boundaries; query keys stay session-global

v1.2 roadmap decisions (from research, 2026-07-13):

- Shakedown first: discovery-driven scope must be known early; safe-area/viewport fixes alter layout globally
- E2E targets the bundled monolith jar (the artifact Render runs); users seeded via /api/auth/register, never SQL fixtures; Chromium-only in CI
- Profile photos via server-side Cloudinary proxy — no client-exposed credentials or unsigned presets (quota-abuse risk)
- Genre data is NET-NEW (books table has no subject column; D-05 prohibits genreBreakdown) — STATS-07/08 semantics resolved at Phase 14 discuss-phase, independently cuttable
- reading_activity stores dates only (anti-forgery) — pace trends framed as finish-rate/activity-density, never page-delta history
- All new user-specific server state goes through TanStack Query, or its cleanup joins clearAuthSession in the same commit (milestone rule)
- [Phase ?]: JPQL string literal CURRENTLY_READING (not enum ref) for bidirectional friends-reading query with mandatory explicit countQuery
- [Phase ?]: D-04: Store Cloudinary secure_url directly in DB; not public_id
- [Phase ?]: Apache Tika magic-byte MIME detection (not Content-Type); HEIC/HEIF allowed for iOS
- [Phase ?]: @WebMvcTest photo endpoints: SecurityMockMvcRequestPostProcessors.user(UserEntity) + csrf() required
- [Phase ?]: photoUrl propagated through all 6 social/notification DTOs (D-11: null when no photo set)
- [Phase ?]: Cloudinary test stubs added to test application.properties so @SpringBootTest integration tests can boot without CLOUDINARY_* env vars

### Pending Todos

None.

### Blockers/Concerns

- testcontainers.version=1.21.4 override in pom.xml: required for Docker 29.x WSL2 until Spring Boot BOM upgrades its managed version
- Phase 14 research flag: genre projection semantics + backfill strategy need a discuss-phase decision before planning (do not let planning guess)
- Phase 16 research flag: HEIC handling decision (accept-and-convert via Cloudinary vs. reject); verify Cloudinary free-tier behavior at account creation before committing the plan

### Roadmap Evolution

- v1.1: Phase 12 edited — merged Phase 13 (Reading Streaks & Pace Projections) into Phase 12; milestone shipped as 3 phases
- v1.2: Roadmap created 2026-07-13 — Phases 13–17 (Mobile Shakedown, Stats Enrichment, Playwright E2E, Profile Photos [cuttable], Portfolio Close-out)

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| v2 | Crop/zoom preview UI before photo upload | Cut for deadline | v1.2 requirements |
| v2 | Pages-per-day time series | Needs page-level activity data | v1.2 requirements |
| v2 | Per-genre trend lines | Sparse-data noise | v1.2 requirements |
| v2+ | Goodreads CSV import | Out of scope | Init |
| v2+ | Social/notification E2E flows | Flakiest E2E category | v1.2 requirements |

## Session Continuity

Last session: 2026-07-22T16:34:11.475Z
Stopped at: context exhaustion at 76% (2026-07-22)
Resume file: None
Next action: DOCS-05 — push v1.2 tag (git tag -a v1.2 -m "v1.2 Mobile Shakedown & Close-out" && git push origin v1.2)

## Performance Metrics

| Phase | Plan | Duration | Notes |
|-------|------|----------|-------|
| Phase 10 P01 | 145 | 3 tasks | 2 files |
| Phase 10 P03 | 149 | 3 tasks | 5 files |
| Phase 10 P02 | 415 | 2 tasks | 2 files |
| Phase 11 P01 | 9min | 4 tasks | 8 files |
| Phase 11 P02 | 7min | 4 tasks | 14 files |
| Phase 11 P03 | 4min | 2 tasks | 4 files |
| Phase 12 P01 | 13min | 2 tasks | 7 files |
| Phase 12 P04 | 4min | 2 tasks | 5 files |
| Phase 12 P02 | 6min | 2 tasks | 6 files |
| Phase 12 P03 | ~35 min | 3 tasks | 6 files |
| Phase 12 P05 | 65min | 2 tasks | 11 files |
| Phase 12 P06 | 15min | 2 tasks | 7 files |
| Phase 12 P07 | 7m | 2 tasks | 8 files |
| Phase 12 P08 | 8m | 2 tasks | 8 files |
| Phase 15 P01 | 7min | 3 tasks | 3 files |
| Phase 15 P02 | 4min | 2 tasks | 5 files |
| Phase 15 P03 | 5min | 2 tasks | 3 files |
| Phase 17 P01 | 8min | 3 tasks | 12 files |
| Phase 17 P02 | 10min | 4 tasks | 12 files |
| Phase 17 P03 | 3min | 4 tasks | 8 files |
| Phase 18 P01 | 8min | 4 tasks | 3 files |

## Quick Tasks Completed

| Slug | Date | Summary |
|------|------|---------|
| drop-follow-system | 2026-07-16 | Removed follower/following system; replaced with friends-only (friendCount). V9 migration drops follows table. 16 files changed, 828 lines deleted. |

## Operator Next Steps

- Plan the first v1.2 phase with /gsd-plan-phase 13
- MOB-01 (version string in UI) must be the first commit of Phase 13
