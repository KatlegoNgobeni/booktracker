# BookTracker

[![CI](https://github.com/KatlegoNgobeni/booktracker/actions/workflows/ci.yml/badge.svg)](https://github.com/KatlegoNgobeni/booktracker/actions/workflows/ci.yml)

A mobile-first personal reading tracker — your Goodreads, done right. Log books, track reading progress, rate and review, set a yearly reading goal, and see personal analytics, all from a PWA you can install on your phone.

**Live app:** [https://booktracker-m0tr.onrender.com](https://booktracker-m0tr.onrender.com)

> **Note:** the free-tier Render web service spins down after ~15 minutes of inactivity.
> The first request after idle takes ~30 seconds (cold start). This is expected behaviour, not a bug.

---

## Quick start (local)

**Prerequisites:** Docker, Docker Compose

```bash
cp .env.example .env   # fill in POSTGRES_* and JWT_SECRET
docker compose up
# App available at http://localhost:8080
```

The app runs as a bundled monolith — the Spring Boot JAR serves both the REST API (`/api/**`) and the React SPA (`/`).

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4, Spring Security, Flyway, PostgreSQL 16 |
| Frontend | React 18, Vite 5, TanStack Query, Tailwind CSS, PWA (vite-plugin-pwa) |
| Auth | JWT + BCrypt (stateless — no sessions) |
| Deploy | Render (Docker web service + managed PostgreSQL 16) |

---

## Architecture

- **Bundled monolith:** The Vite build output is embedded into the Spring Boot fat JAR at Docker build time (Stage 1 → Node/Vite, Stage 2 → Maven, Stage 3 → JRE). No CORS needed; frontend and API share the same origin.
- **SPA routing:** A Spring `WebMvcConfigurer` serves `index.html` for all extension-free routes so React Router's `BrowserRouter` works in production.
- **JWT auth:** Stateless — tokens are stored in `localStorage` with refresh handled client-side.

---

## CI / CD

Every push triggers the `.github/workflows/ci.yml` workflow:

1. **Backend tests:** `mvn verify -B` — runs all JUnit 5 + Testcontainers integration tests against a real PostgreSQL 16 container.
2. **Frontend build:** `npm ci && npm run build` — TypeScript type-check (`tsc -b`) + Vite compile.
3. **Deploy (main only):** On push to `main`, calls the Render deploy hook via `curl --fail` (hook URL stored as the `RENDER_DEPLOY_HOOK_URL` GitHub Actions secret — never in code).

---

## Environment variables

| Variable | Where set | Description |
|----------|-----------|-------------|
| `SPRING_DATASOURCE_URL` | Render Environment tab | JDBC URL: `jdbc:postgresql://<host>:<port>/<db>` |
| `SPRING_DATASOURCE_USERNAME` | Render Environment tab | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | Render Environment tab | PostgreSQL password |
| `JWT_SECRET` | Render Environment tab | Long random string (`openssl rand -base64 48`) |
| `POSTGRES_*` | `.env` (local only, gitignored) | Local Docker Compose database config |
