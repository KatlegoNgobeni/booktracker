# BookTracker

## What This Is

A personal reading-tracker web app — your "Goodreads, done right." Users log books, track reading progress, rate and review, set a yearly reading goal, and see personal analytics, all from a mobile-first PWA. Built as a portfolio-grade software engineering project targeting Entelect and BBD graduate roles.

## Core Value

A working app you actually use on your phone every day — plus the ability to confidently whiteboard and extend every layer of it in an interview.

## Requirements

### Validated

(None yet — ship to validate)

### Active

**MVP (Phase 1–9):**
- [ ] User registration and login (email/password, JWT auth, BCrypt)
- [ ] Search books via Open Library API (proxied, paginated)
- [ ] Add book to shelf with status (WANT_TO_READ / CURRENTLY_READING / READ)
- [ ] Cache book data locally on first fetch (fetch-once strategy)
- [ ] Update reading progress (current page, auto-finish on READ)
- [ ] Rate (1–5) and review books on shelf entries
- [ ] Set and track a yearly reading goal
- [ ] Stats/analytics page (books read, genre breakdown, pages, goal progress, charts)
- [ ] Mobile-first React PWA (installable, offline shell)
- [ ] Ownership enforcement: users can only modify their own shelf entries
- [ ] Deployed with CI/CD (GitHub Actions green badge, live URL)
- [ ] `docker-compose up` runs the full stack locally

**Social layer (v2, before mid-August):**
- [ ] Follow / unfollow other users (FR-13, no self-follow)
- [ ] Public profiles: view another user's display name, goal progress, READ shelf with reviews (FR-14)
- [ ] Activity feed: recent finishes and reviews from followed users (FR-15)

### Out of Scope

- Goodreads CSV import — deferred to post-v2 (adds scope without affecting interview talking points)
- Richer stats (pace projections, reading streaks) — deferred to v3
- Normalized authors table — using comma-joined varchar for MVP as per spec
- OAuth / social login — not needed; email/password is sufficient for the target audience
- Native mobile app — PWA covers the phone-use case

## Context

- **Target employers:** Entelect and BBD — SA enterprise software consultancies (Java/.NET). They assess engineering fundamentals: clean relational modelling, proper auth, RESTful API design, tests, deployment.
- **Meta goal:** the repo is evidence. Being able to explain and extend every layer live in an interview is the actual deliverable. Every shortcut taken is a question you can't answer.
- **Timeline:** MVP + social fully functional by end of July to mid-August 2026 (~5–7 weeks from project start on 2026-06-22).
- **Dev environment:** Windows desktop (16 GB RAM), WSL2. `wsl --install` has been run; full toolchain (JDK 21, Node, Docker Desktop + WSL2 integration) still needs to be set up — this is the first blocker before any code can run.
- **External dependency:** Open Library API (free, no key). Must set `User-Agent` header and cache data per their etiquette. Page count often absent from work-level data — stats that need it gracefully skip nulls.

## Constraints

- **Tech stack**: Java 21 + Spring Boot 3 (Web, Data JPA, Security, Validation, Flyway), React 18 + Vite, PostgreSQL 16 — locked. Rationale: builds on existing Java + React knowledge; Spring Boot mirrors the BBD/Entelect enterprise stack.
- **Auth**: JWT + BCrypt. No OAuth for MVP.
- **Database**: PostgreSQL via Docker locally. UUID PKs throughout. Flyway for versioned migrations.
- **Deployment**: deploy target deferred — Cloud Run + Netlify/Vercel (CV-impressive) vs Render/Railway (simpler) to be decided at Milestone 8.
- **ORM strategy**: `@Enumerated(EnumType.STRING)` for shelf_status — never ORDINAL. Flyway migrations are immutable once applied.
- **Performance**: paginate all list endpoints; index `(user_id, shelf_status)` and FK columns; use JOIN FETCH to avoid N+1 on shelf listings.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Java + Spring Boot (not C#/.NET) | Builds on existing Java knowledge; Spring Boot is enterprise-credible for Entelect/BBD without language-learning cost | — Pending |
| PostgreSQL over MySQL | Production-grade, enterprise-standard; Entelect/BBD run it | — Pending |
| Open Library over Google Books | Free, no API key; simpler setup for a portfolio project | — Pending |
| Cache-or-fetch book data locally | Respects Open Library etiquette (low-volume); stats run as fast local SQL; resilient to API downtime | — Pending |
| UUID PKs throughout | Avoids sequential ID enumeration (IDOR defense); industry standard for new APIs | — Pending |
| Social layer in same repo as MVP | Continuity of data model; follows/feeds build on the same user_books + users tables | — Pending |
| 403 (not 404) for ownership violations | Explicit spec decision — signals engineering maturity; be ready to defend the tradeoff (404 leaks less, 403 is clearer) | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-22 after initialization*
