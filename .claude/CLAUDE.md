<!-- GSD:project-start source:PROJECT.md -->

## Project

**BookTracker**

A personal reading-tracker web app — your "Goodreads, done right." Users log books, track reading progress, rate and review, set a yearly reading goal, and see personal analytics, all from a mobile-first PWA. Built as a portfolio-grade software engineering project targeting Entelect and BBD graduate roles.

**Core Value:** A working app you actually use on your phone every day — plus the ability to confidently whiteboard and extend every layer of it in an interview.

### Constraints

- **Tech stack**: Java 21 + Spring Boot 3 (Web, Data JPA, Security, Validation, Flyway), React 18 + Vite, PostgreSQL 16 — locked. Rationale: builds on existing Java + React knowledge; Spring Boot mirrors the BBD/Entelect enterprise stack.
- **Auth**: JWT + BCrypt. No OAuth for MVP.
- **Database**: PostgreSQL via Docker locally. UUID PKs throughout. Flyway for versioned migrations.
- **Deployment**: deploy target deferred — Cloud Run + Netlify/Vercel (CV-impressive) vs Render/Railway (simpler) to be decided at Milestone 8.
- **ORM strategy**: `@Enumerated(EnumType.STRING)` for shelf_status — never ORDINAL. Flyway migrations are immutable once applied.
- **Performance**: paginate all list endpoints; index `(user_id, shelf_status)` and FK columns; use JOIN FETCH to avoid N+1 on shelf listings.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## Recommended Stack

### Backend Core

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Java | 21 (LTS) | Runtime | Minimum for Spring Boot 3.2+; virtual threads (Project Loom) available; long-term support aligns with enterprise interview credibility |
| Spring Boot | 3.4.x | Application framework | Latest stable 3.x; auto-configuration, embedded Tomcat, production actuator. Use the Spring Initializr to pin to 3.4.x — do NOT use 3.0/3.1 (they are EOL) |
| Spring Web (MVC) | (via Boot) | REST controllers | `@RestController`, `ResponseEntity`, `@Valid` — standard; nothing to migrate here |
| Spring Data JPA | (via Boot) | ORM / repository layer | Backed by Hibernate 6.x in Spring Boot 3.x; replaces all Hibernate 5 APIs |
| Spring Security | 6.4.x (via Boot 3.4) | Auth, authorization | SecurityFilterChain approach — see dedicated section below |
| Spring Validation | (via Boot) | Bean Validation 3.0 | `jakarta.validation.*` — replaces `javax.validation.*` entirely |
| Flyway | 10.x (via Boot) | DB migrations | Versioned SQL migrations; Spring Boot auto-runs on startup |
| PostgreSQL Driver | 42.7.x | JDBC driver | `org.postgresql:postgresql` — Boot manages version via BOM |

### Frontend Core

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| React | 18.3.x | UI framework | Concurrent mode, Suspense, stable; 19 is RC — do not use in portfolio work yet |
| Vite | 5.x | Build tool | Fastest dev server; native ESM; replaces CRA (deprecated) entirely |
| vite-plugin-pwa | 0.21.x | PWA manifest + service worker | Wraps Workbox; generates SW and web app manifest; actively maintained |
| React Router | 6.x (v6.4+) | Client-side routing | Data Router API (loaders/actions) available but optional; use for page navigation |
| TanStack Query (React Query) | 5.x | Server state / API caching | Eliminates manual loading/error state; pairs perfectly with the cache-or-fetch pattern |
| Axios | 1.7.x | HTTP client | More predictable error handling than fetch for JWT interceptor pattern; or use fetch + interceptor if keeping deps minimal |
| Recharts | 2.x | Charts for analytics page | Composable, React-native, lightweight — sufficient for goal/genre/pages charts |

### Database

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| PostgreSQL | 16 | Primary store | Enterprise-standard; UUID native type; JSONB if needed; runs in Docker for local dev |
| Docker / docker-compose | Latest | Local dev orchestration | `docker-compose up` starts pg + app as required by PROJECT.md |

### Testing

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| JUnit 5 (Jupiter) | 5.10.x (via Boot) | Unit + integration tests | `@ExtendWith(MockitoExtension.class)` for unit; `@SpringBootTest` for integration |
| Mockito | 5.x (via Boot) | Mocking | Industry standard; `@MockitoBean` replaces `@MockBean` in Spring Boot 3.4+ |
| Spring Boot Test | (via Boot) | Test slices | `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest` — use slices not full context where possible |
| Testcontainers | 1.19.x | Real PostgreSQL in tests | Use `@DataJpaTest` + Testcontainers PostgreSQL for JPA layer tests — no H2 in-memory (H2 does not support PostgreSQL-specific SQL) |
| Vitest | 2.x | Frontend unit tests | Vite-native; same config file; faster than Jest |
| React Testing Library | 14.x | Component testing | Test behaviour not implementation; pairs with Vitest |
| MSW (Mock Service Worker) | 2.x | API mocking in tests | Intercept fetch/axios in Vitest + browser; avoids manual axios mocking |

## Version Gotchas: Spring Boot 2.x → 3.x

### 1. Jakarta EE namespace — the biggest breaking change

- `javax.persistence.*` → `jakarta.persistence.*`
- `javax.validation.*` → `jakarta.validation.*`
- `javax.servlet.*` → `jakarta.servlet.*`
- `javax.transaction.*` → `jakarta.transaction.*`

### 2. Hibernate 6 changes (Spring Boot 3.x bundles Hibernate 6)

- `@GenericGenerator` with `strategy = "uuid2"` is **removed** in Hibernate 6. Use `GenerationType.UUID` instead (see UUID section).
- Hibernate 6 changed implicit naming strategy — column/table names that worked in Hibernate 5 may differ. Use explicit `@Column(name = "...")` and `@Table(name = "...")` to be safe.
- `@Type(type = "uuid-char")` (Hibernate 5) is gone. PostgreSQL stores UUIDs natively; Hibernate 6 maps `UUID` Java type to the `uuid` column type automatically.

### 3. Spring Security 6 — antMatchers removed

### 4. Spring Security 6 — lambda DSL required

### 5. @MockBean → @MockitoBean in Spring Boot 3.4

### 6. Spring Boot 3.2 RestClient (replaces RestTemplate)

### 7. spring.jpa.open-in-view=false — set this explicitly

## Spring Security 6: Current SecurityFilterChain Approach

### Complete JWT security config pattern

### JwtAuthenticationFilter pattern (OncePerRequestFilter)

### JWT library: use jjwt-api 0.12.x

## UUID Primary Keys + JPA + PostgreSQL — Gotchas

### Recommended pattern (Hibernate 6 / Spring Boot 3.x)

### Flyway migration for UUID columns

### Enum strategy

## Flyway + Spring Boot 3 Setup

### Maven dependency

### application.properties

## React 18 + Vite + PWA Setup

### Vite project setup

# or for TypeScript (recommended for portfolio credibility):

### vite-plugin-pwa installation and configuration

- `registerType: 'autoUpdate'` — service worker updates silently in background. Alternatively `'prompt'` shows a "new version available" UI.
- `workbox.runtimeCaching` — cache Open Library cover images with CacheFirst (they never change for a given cover ID).
- `display: 'standalone'` — hides browser UI on mobile, making the app feel native.

## Open Library API — Known Quirks

### Search endpoint

| Field | Availability | Notes |
|-------|-------------|-------|
| `title` | Always present | Safe to use |
| `author_name[]` | Usually present | Can be missing for older works; array — use first element |
| `cover_i` | Often present | The cover image ID; absent for books without covers |
| `number_of_pages_median` | Frequently absent | Median across editions; null for many books — never treat as required |
| `first_publish_year` | Usually present | Can be null for very new or unverified entries |
| `isbn[]` | Often present | Array of ISBNs across all editions |
| `subject[]` | Sometimes present | Used for genre inference; often very broad or absent |
| `key` | Always present | Format `/works/OL123W` — use this as the stable work identifier |
| `edition_key[]` | Present | Array of edition keys |

## Testing Setup

### Backend: JUnit 5 + Mockito test slices

### Frontend: Vitest + React Testing Library

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Build tool | Vite 5.x | Create React App | CRA is officially deprecated; no longer maintained |
| Build tool | Vite 5.x | Next.js | SSR/SSG overhead not needed; SPA is sufficient for this use case |
| JWT library | jjwt 0.12.x | spring-security-oauth2-resource-server | OAuth2 module adds complexity for simple custom JWT auth; harder to explain in interview |
| HTTP client (backend) | Spring RestClient | WebClient | WebClient is reactive (Project Reactor); unnecessary complexity for blocking MVC app |
| HTTP client (backend) | Spring RestClient | RestTemplate | RestTemplate is in maintenance mode; RestClient is the Spring-endorsed replacement |
| DB migrations | Flyway | Liquibase | Flyway is simpler SQL-based migrations; Liquibase XML format is less readable for a portfolio project |
| Charts | Recharts | Chart.js + react-chartjs-2 | Recharts is React-native (no canvas wrapper); smaller bundle for same use case |
| Auth token storage | localStorage | httpOnly cookie | httpOnly cookie is more XSS-resistant but requires same-origin or CORS cookie config; localStorage with short expiry + refresh token flow is the industry-standard React SPA approach and simpler to implement correctly |
| State management | TanStack Query | Redux Toolkit | Redux adds boilerplate for server state; TanStack Query handles loading/caching/error patterns that would otherwise need Redux |
| Test database | Testcontainers PostgreSQL | H2 in-memory | H2 doesn't support PostgreSQL-specific DDL in Flyway migrations |

## Maven pom.xml Starter Configuration

## Sources

- Spring Security 6 `SecurityFilterChain` docs — fetched live 2026-06-22 (docs.spring.io/spring-security/reference/servlet/configuration/java.html) — MEDIUM confidence
- Spring Boot 3.x upgrade guide — fetched live 2026-06-22 (docs.spring.io) — MEDIUM confidence
- jjwt 0.12.x API, Hibernate 6 UUID behaviour, Flyway 10 PostgreSQL module split, vite-plugin-pwa configuration, Open Library API field availability — training knowledge (cutoff Aug 2025) — MEDIUM confidence
- `@MockitoBean` replacing `@MockBean` in Spring Boot 3.4 — training knowledge — MEDIUM confidence

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
