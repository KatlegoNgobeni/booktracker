---
phase: "14"
plan: "02"
subsystem: backend-stats-subjects
status: complete
tags: [stats, subjects, flyway, bookentity, openlibrary, topgenre, react]

dependency_graph:
  requires:
    - "14-01: StatsPage.tsx with plan-01 streak/goal changes (STATS-01 through STATS-06) — applied as base in this plan because plan-02 worktree diverged from main before plan-01 merged"
    - "V7__reading_streaks.sql: most recent Flyway migration (V8 must follow V7)"
    - "BookEntity: existing entity with standard JPA pattern (subjects added as nullable field)"
    - "StatsService: existing getStats() method with readThisYear list already loaded"
  provides:
    - "STATS-07: V8 Flyway migration adding nullable varchar(1000) subjects column to books table"
    - "STATS-07: OpenLibraryWorkResponse.subjects List<String> field with @JsonProperty"
    - "STATS-07: BookEntity.subjects String field with getter/setter"
    - "STATS-07: BookService.toEntity() maps subjects as pipe-delimited string (1000-char cap)"
    - "STATS-08: StatsDto.topGenre nullable String field (NON_NULL JSON serialization)"
    - "STATS-08: StatsService.getStats() computes topGenre from this year's READ books in-memory"
    - "STATS-08: api.types.ts topGenre?: string optional field in StatsDto interface"
    - "STATS-08: StatsPage.tsx topGenre col-span-2 card in All Time section (conditional)"
  affects:
    - "src/main/resources/db/migration/V8__book_subjects.sql"
    - "src/main/java/com/booktracker/books/BookEntity.java"
    - "src/main/java/com/booktracker/books/OpenLibraryWorkResponse.java"
    - "src/main/java/com/booktracker/books/BookService.java"
    - "src/main/java/com/booktracker/stats/StatsDto.java"
    - "src/main/java/com/booktracker/stats/StatsService.java"
    - "frontend/src/types/api.types.ts"
    - "frontend/src/pages/stats/StatsPage.tsx"

tech_stack:
  added:
    - "java.util.Map and java.util.stream.Collectors imports in StatsService (for topGenre stream)"
  patterns:
    - "Pipe-delimited string storage: subjects persisted as pipe-joined String (avoids join table, sufficient for portfolio scope)"
    - "varchar(1000) cap via Java substring before persist (mirrors T-14-05 threat mitigation)"
    - "In-memory topGenre computation: reuse existing readThisYear list, no extra DB query"
    - "Mutable non-final field (topGenre) on otherwise-immutable DTO — setter called post-construction via Optional.ifPresent"
    - "Conditional React card: stats.topGenre != null gates rendering (mirrors longestBook/shortestBook pattern)"

key_files:
  created:
    - "src/main/resources/db/migration/V8__book_subjects.sql — ALTER TABLE books ADD COLUMN subjects varchar(1000)"
  modified:
    - "src/main/java/com/booktracker/books/BookEntity.java — subjects field added"
    - "src/main/java/com/booktracker/books/OpenLibraryWorkResponse.java — subjects deserialization field added"
    - "src/main/java/com/booktracker/books/BookService.java — toEntity() subjects mapping with 1000-char cap"
    - "src/main/java/com/booktracker/stats/StatsDto.java — topGenre String field (mutable, NON_NULL)"
    - "src/main/java/com/booktracker/stats/StatsService.java — topGenre computation via stream groupingBy"
    - "frontend/src/types/api.types.ts — topGenre?: string added to StatsDto interface"
    - "frontend/src/pages/stats/StatsPage.tsx — STATS-01/02/03/04/05/06 base + STATS-08 topGenre card"

decisions:
  - "Pipe-delimited string vs join table for subjects: pipe-delimited String chosen for simplicity — avoids a subjects join table, sufficient cardinality for genre inference (first token), no query complexity added"
  - "varchar(1000) cap in Java (not SQL): cap applied in BookService.toEntity() before persist to prevent silent truncation on the PostgreSQL side; STATS-07 threat T-14-05 specifies mitigation at this layer"
  - "In-memory topGenre computation: reuses readThisYear List<UserBookEntity> already fetched for booksPerMonth; avoids a new JPQL query or N+1 on book subjects; acceptable for portfolio scale"
  - "Mutable topGenre on immutable DTO: all other StatsDto fields are final and set in constructor; topGenre is added as a non-final field with setter to allow Optional.ifPresent(dto::setTopGenre) post-construction without changing the constructor signature (would require updating all 11 StatsServiceTests)"
  - "StatsPage.tsx worktree base: worktree diverged from main before plan-01 (e1e7b09) merged; plan-02 applied plan-01's full StatsPage.tsx as base (STATS-01 through STATS-06) then added STATS-08 topGenre card"
  - "topGenre uses this year filter (readThisYear) not all-time: the metric answers 'what am I reading this year' not 'what have I ever read' — matches the per-year framing of booksReadThisYear"

metrics:
  duration: "18 minutes"
  completed: "2026-07-15"
  tasks_completed: 2
  files_created: 1
  files_modified: 7
  backend_tests_total: 169
  frontend_tests_total: 11
---

# Phase 14 Plan 02: Stats Enrichment — Subjects + Top Genre Summary

**One-liner:** V8 Flyway migration adds nullable subjects column; pipe-delimited subject storage from Open Library; topGenre stat computed in-memory from this year's READ books and rendered as a conditional card.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | V8 migration + BookEntity subjects field + OpenLibraryWorkResponse deserialization + BookService mapping (STATS-07) | 72978cd | V8__book_subjects.sql, BookEntity.java, OpenLibraryWorkResponse.java, BookService.java |
| 2 | StatsDto topGenre field + StatsService computation + frontend topGenre card (STATS-08) | 39d8e2f | StatsDto.java, StatsService.java, api.types.ts, StatsPage.tsx |

## What Was Built

### STATS-07: Subject Storage Pipeline

Added a nullable `subjects varchar(1000)` column to the `books` table via V8 Flyway migration. The Open Library works API includes a `subjects` array (e.g. `["Fiction", "Literature", "Classic novels"]`) which `OpenLibraryWorkResponse` now deserializes. `BookService.toEntity()` joins the list as a pipe-delimited string with a 1000-character cap before persisting to `BookEntity.subjects`. Existing books retain `subjects = NULL`; no backfill is planned.

### STATS-08: Top Genre Projection

`StatsService.getStats()` computes `topGenre` in-memory from the already-loaded `readThisYear` list (no extra DB query). It splits each book's subjects by pipe, takes the first token, groups by token frequency, and picks the most common. The result is set on `StatsDto.topGenre` via `Optional.ifPresent`. When no subject data is available (fresh account, no subjects fetched yet), `topGenre` is null and absent from the JSON response.

The frontend `StatsPage.tsx` renders a `col-span-2` card with the genre label and "Top genre this year" sub-label, conditional on `stats.topGenre != null`.

## Verification Results

- `mvn test` (169 tests): BUILD SUCCESS, 0 failures
- `npx vitest run StatsPage.test.tsx` (11 tests): all passed
- `npx tsc --noEmit`: exits 0 (no type errors)
- V8 migration applied in Testcontainers context: "Migrating schema to version 8 - book subjects" logged

## Deviations from Plan

### Rule 1 Auto-applied: StatsPage.tsx includes STATS-01/02/03/04/05/06 base from plan-01

**Found during:** Task 2 implementation

**Issue:** This agent's worktree diverged from `main` at commit `8caa7ca` (before the plan-01 agent merged `ad554c4`). The worktree's `StatsPage.tsx` was the pre-plan-01 version (old streak cards, no STATS-01 through STATS-06 changes). The test file in the worktree already had the plan-01 test updates (tests 4–10 expecting the new streak/goal design).

**Fix:** Applied the plan-01 version of `StatsPage.tsx` (from commit `e1e7b09` on main) as the base before adding the STATS-08 topGenre card. The resulting file includes all STATS-01 through STATS-08 changes and passes all 11 frontend tests.

**Files modified:** `frontend/src/pages/stats/StatsPage.tsx`

**Commit:** 39d8e2f

## Known Stubs

None — all fields are wired. `topGenre` will naturally be absent from responses until books accumulate subjects via Open Library fetches.

## Threat Flags

No new threat surface beyond the plan's threat model (T-14-03 through T-14-06, all accepted or mitigated).

## Self-Check: PASSED

- V8__book_subjects.sql exists and contains additive ALTER TABLE: FOUND
- BookEntity.subjects field with getter/setter: FOUND
- OpenLibraryWorkResponse.subjects List<String>: FOUND
- BookService.toEntity() subjects mapping with 1000-char cap: FOUND
- StatsDto.topGenre String field: FOUND
- StatsService topGenre computation: FOUND
- api.types.ts topGenre?: string: FOUND
- StatsPage.tsx topGenre card: FOUND
- Commits 72978cd and 39d8e2f: VERIFIED
- Backend tests 169/169 passed: VERIFIED
- Frontend tests 11/11 passed: VERIFIED
