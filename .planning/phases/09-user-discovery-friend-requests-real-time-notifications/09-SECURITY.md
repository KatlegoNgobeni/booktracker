---
phase: "09"
slug: user-discovery-friend-requests-real-time-notifications
status: secured
threats_open: 0
asvs_level: 1
created: 2026-07-06
---

# Phase 09 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| client → /api/friend-requests | Untrusted request id + recipientId | UUID identifiers; identity must come from JWT, not body |
| client → /api/users/search | Untrusted `q` string | Search query string; crosses into JPQL |
| client → /api/entries/{entryId}/like | Untrusted entryId | UUID; liker identity from JWT only |
| client → /api/notifications/** | Untrusted requests | Notification data; scope locked to JWT user |
| client → /ws (SockJS + STOMP) | WebSocket upgrade + STOMP CONNECT | JWT presented in CONNECT native header, not URL |
| server response → React render | Display names, review text, search results | User-controlled strings rendered into DOM |
| build → Maven BOM (spring-boot-starter-websocket) | First-party Spring starter | Build-time dependency; BOM-managed version |
| build → npm registry (@stomp/stompjs, sockjs-client) | Third-party WebSocket client libs | Build-time; versions pinned to RESEARCH-approved releases |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-09-01 | Elevation of Privilege | acceptRequest / rejectRequest | high | mitigate | `findById→404` then `recipient.id == currentUser.id` else 403; recipient from persisted row, never request | closed |
| T-09-02 | Elevation of Privilege | cancelRequest | high | mitigate | `findById→404` then `requester.id == currentUser.id` else 403 | closed |
| T-09-03 | Spoofing | all friend-request endpoints | high | mitigate | Identity from `@AuthenticationPrincipal UserEntity`; recipientId in body is target only, never actor | closed |
| T-09-04 | Tampering / Injection | GET /api/users/search q param | medium | mitigate | JPQL parameterized `:query` with `LIKE CONCAT('%',:query,'%')` bind — no string concatenation | closed |
| T-09-05 | Info Disclosure | user search results | low | accept | Only `id` + `displayName` exposed (D-06); no email or private shelf data returned | closed |
| T-09-06 | Spoofing (self-relationship) | sendRequest | medium | mitigate | `recipientId.equals(currentUser.getId())` → 400 before any DB write; DB no-self CHECK as defense-in-depth | closed |
| T-09-07 | Spoofing | likeReview | high | mitigate | Liker is `@AuthenticationPrincipal UserEntity` from JWT — never a userId from request | closed |
| T-09-08 | Elevation of Privilege | unlikeReview | high | mitigate | Delete scoped to `findByUserIdAndEntryId(currentUser.getId(), entryId)` — only caller's own like removable | closed |
| T-09-09 | Tampering | like on non-existent entry | low | mitigate | Entry existence verified → 404; FK constraint on user_books is defense-in-depth | closed |
| T-09-10 | DoS | mass like spam | low | accept | `review_likes_pair_uq` prevents duplicate rows; no rate limiting in MVP | closed |
| T-09-11 | Spoofing | WebSocket identity | high | mitigate | JWT validated in `JwtChannelInterceptor` at STOMP CONNECT; principal cannot be overridden by client frames | closed |
| T-09-12 | Info Disclosure | unauthenticated WS subscription | high | mitigate | No principal → `/user/queue/**` delivers to nobody; `convertAndSendToUser` keyed by validated UUID principal | closed |
| T-09-13 | Info Disclosure | GET /api/notifications | high | mitigate | Always scoped to `currentUser.getId()` from `@AuthenticationPrincipal` — no userId accepted from request | closed |
| T-09-14 | Info Disclosure | JWT exposure in transport | medium | mitigate | Token in STOMP CONNECT native header, never as `/ws` query param (avoids access-log leakage) | closed |
| T-09-15 | Tampering | mark-read of another user's notifications | high | mitigate | `markAllReadForUser` UPDATE filtered by `user.id = currentUser.getId()` | closed |
| T-09-16 | Tampering (XSS) | search results / review text / request names | medium | mitigate | Render via JSX text interpolation only; no `dangerouslySetInnerHTML` in any new components | closed |
| T-09-17 | Spoofing | friend/like mutations | high | mitigate | Actor identity from JWT via axios interceptor; request bodies never carry the acting user id | closed |
| T-09-18 | Info Disclosure | self friend-request button | low | mitigate | `FriendRequestButton` hidden for current user (`userId === currentUserId` guard in `UserPublicProfilePage`) | closed |
| T-09-19 | Spoofing | STOMP CONNECT | high | mitigate | JWT sent in STOMP `connectHeaders` (`Authorization: Bearer`), not in the `/ws` URL | closed |
| T-09-20 | Info Disclosure | subscription channel | high | mitigate | Client subscribes to `/user/queue/notifications` only; server routes per authenticated principal — no cross-user leakage | closed |
| T-09-21 | Tampering (XSS) | notification text render | medium | mitigate | Composed via JSX text interpolation from typed fields; no `dangerouslySetInnerHTML` | closed |
| T-09-SC | Tampering | spring-boot-starter-websocket install | high | mitigate | First-party Spring starter, BOM-managed version, approved in RESEARCH Package Legitimacy Audit | closed |
| T-09-SC-FE | Tampering | @stomp/stompjs / sockjs-client install | high | mitigate | Versions pinned to RESEARCH-approved releases; all three packages verified OK in Package Legitimacy Audit | closed |

*Status: closed — all 23 threats closed. threats_open: 0.*
*Severity: critical > high > medium > low*
*Disposition: mitigate (implementation required) · accept (documented risk)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-09-01 | T-09-05 | User search exposes only `id` + `displayName` — no PII beyond a public handle. Acceptable for a social discovery feature. | Katlego | 2026-07-06 |
| AR-09-02 | T-09-10 | No rate limiting on likes in MVP. The `pair_uq` constraint prevents duplicate likes. Rate limiting deferred to Phase 7/post-MVP hardening. | Katlego | 2026-07-06 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-07-06 | 23 | 23 | 0 | Claude gsd-secure-phase (ASVS L1 grep) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
