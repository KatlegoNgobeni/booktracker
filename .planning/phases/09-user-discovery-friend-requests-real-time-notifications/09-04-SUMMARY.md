---
phase: 09-user-discovery-friend-requests-real-time-notifications
plan: 04
subsystem: notifications
tags: [websocket, stomp, sockjs, spring-security, jwt, real-time]

requires:
  - phase: 09-03
    provides: notification entity, repository, service (persist-only), REST endpoints

provides:
  - WebSocketConfig with STOMP-over-SockJS /ws endpoint and JwtChannelInterceptor
  - JwtChannelInterceptor: JWT auth at STOMP CONNECT frame; principal = user UUID
  - SecurityConfig /ws/** permitAll — SockJS HTTP upgrade allowed; auth at STOMP layer
  - NotificationService extended to persist AND push via SimpMessagingTemplate
  - Four notification triggers wired: FRIEND_REQUEST, FRIEND_ACCEPTED, REVIEW_LIKED, FRIEND_FINISHED_BOOK

affects: [09-05-frontend-social-ui, 09-06-frontend-notifications]

tech-stack:
  added: [spring-boot-starter-websocket]
  patterns:
    - JWT auth at STOMP CONNECT layer (not HTTP); HTTP /ws/** permitAll
    - convertAndSendToUser keyed by recipient.getUsername() (UUID string)
    - Synchronous createNotification inside @Transactional — safe no-op when recipient is offline
    - FRIEND_FINISHED_BOOK fan-out via FriendRequestRepository accepted-friends lookup

key-files:
  created:
    - src/main/java/com/booktracker/config/WebSocketConfig.java
    - src/main/java/com/booktracker/security/JwtChannelInterceptor.java
    - src/test/java/com/booktracker/security/JwtChannelInterceptorTest.java
  modified:
    - pom.xml (spring-boot-starter-websocket dependency)
    - src/main/java/com/booktracker/config/SecurityConfig.java (/ws/** permitAll)
    - src/main/java/com/booktracker/notification/NotificationService.java (persist+push)
    - src/main/java/com/booktracker/social/FriendRequestService.java (FRIEND_REQUEST, FRIEND_ACCEPTED triggers)
    - src/main/java/com/booktracker/social/LikeService.java (REVIEW_LIKED trigger, no self-likes)
    - src/main/java/com/booktracker/shelf/ShelfService.java (FRIEND_FINISHED_BOOK trigger on READ transition)
    - src/test/java/com/booktracker/notification/NotificationIntegrationTest.java (four trigger tests)
    - src/test/java/com/booktracker/shelf/ShelfServiceTest.java (mocks for FriendRequestRepository + NotificationService)

key-decisions:
  - "09-04: spring-boot-starter-websocket BOM-managed (no explicit version); first-party Spring starter"
  - "09-04: JWT auth at STOMP CONNECT layer, not HTTP — /ws/** permitAll in SecurityConfig"
  - "09-04: convertAndSendToUser(recipient.getUsername(), /queue/notifications, dto) — UUID string principal"
  - "09-04: createNotification synchronous inside @Transactional; silent no-op on offline recipient"
  - "09-04: FRIEND_FINISHED_BOOK fan-out via FriendRequestRepository (not injecting ShelfService into NotificationService)"

patterns-established:
  - "WebSocket auth: ChannelInterceptor.preSend on STOMP CONNECT, not HTTP filter"
  - "Notification trigger: inject NotificationService into event-owning service; call createNotification after the primary operation"

requirements-completed: [NOTIF-01, NOTIF-02]

coverage:
  - id: D1
    description: "STOMP-over-SockJS /ws endpoint with JWT auth at CONNECT frame; invalid/absent token yields no principal"
    requirement: NOTIF-02
    verification:
      - kind: unit
        ref: "src/test/java/com/booktracker/security/JwtChannelInterceptorTest.java"
        status: pass
    human_judgment: false
  - id: D2
    description: "NotificationService.createNotification persists then pushes to recipient via convertAndSendToUser"
    requirement: NOTIF-01
    verification:
      - kind: integration
        ref: "src/test/java/com/booktracker/notification/NotificationIntegrationTest.java"
        status: pass
    human_judgment: false
  - id: D3
    description: "Four notification triggers: FRIEND_REQUEST, FRIEND_ACCEPTED, REVIEW_LIKED, FRIEND_FINISHED_BOOK"
    requirement: NOTIF-01
    verification:
      - kind: integration
        ref: "src/test/java/com/booktracker/notification/NotificationIntegrationTest.java#notificationPersistedOnFriendRequest,notificationPersistedOnFriendAccepted,notificationPersistedOnReviewLiked,notificationPersistedOnBookFinished"
        status: pass
    human_judgment: false
  - id: D4
    description: "Real-time push to connected STOMP client — requires manual two-browser test"
    requirement: NOTIF-02
    verification: []
    human_judgment: true
    rationale: "Live WebSocket push to a connected browser cannot be verified by JUnit; requires manual two-browser UAT (per 09-VALIDATION.md)"

duration: ~45min
completed: 2026-07-05
status: complete
---

# Phase 09 Plan 04: Real-time WebSocket Notifications Summary

**STOMP-over-SockJS /ws endpoint with JWT CONNECT-frame auth, four notification triggers wired into FriendRequestService/LikeService/ShelfService, and NotificationService extended to persist-and-push**

## Performance

- **Duration:** ~45 min
- **Completed:** 2026-07-05
- **Tasks:** 3 (RED → GREEN → GREEN)
- **Files modified:** 9

## Accomplishments

- WebSocketConfig registers STOMP endpoint `/ws` with SockJS fallback, `/topic` and `/queue` simple broker, `/user` destination prefix, and JwtChannelInterceptor on the client inbound channel
- JwtChannelInterceptor authenticates STOMP CONNECT frames by validating the Bearer JWT native header; sets the session principal to the user UUID; absent/invalid token proceeds without principal (no connection rejection)
- SecurityConfig updated: `/ws/**` permitAll inserted before `/api/**` authenticated — SockJS HTTP upgrade is now allowed without a Bearer token in the HTTP request
- NotificationService augmented: SimpMessagingTemplate injected; `createNotification` now persists then calls `convertAndSendToUser(recipient.getUsername(), "/queue/notifications", dto)` — a safe no-op when the recipient is offline
- All four NOTIF-01 triggers wired: FriendRequestService sends FRIEND_REQUEST on `sendRequest` and FRIEND_ACCEPTED on `acceptRequest`; LikeService sends REVIEW_LIKED (no self-like); ShelfService fans out FRIEND_FINISHED_BOOK to all accepted friends on READ transition

## Task Commits

1. **Task 1: RED — websocket dependency, interceptor unit test, notification trigger integration tests** - `e4e6c8b`
2. **Task 2: GREEN — WebSocketConfig, JwtChannelInterceptor, /ws SecurityConfig, NotificationService push** - `d26a2b3`
3. **Task 3: GREEN — wire four notification triggers** - `ae86157`
4. **Fix: ShelfServiceTest mocks for new constructor params** - `035c736`

## Files Created/Modified

- `src/main/java/com/booktracker/config/WebSocketConfig.java` — STOMP broker config + SockJS endpoint + interceptor registration
- `src/main/java/com/booktracker/security/JwtChannelInterceptor.java` — JWT auth at STOMP CONNECT layer
- `src/main/java/com/booktracker/config/SecurityConfig.java` — /ws/** permitAll added
- `src/main/java/com/booktracker/notification/NotificationService.java` — SimpMessagingTemplate inject + push after persist
- `src/main/java/com/booktracker/social/FriendRequestService.java` — FRIEND_REQUEST + FRIEND_ACCEPTED triggers
- `src/main/java/com/booktracker/social/LikeService.java` — REVIEW_LIKED trigger (no self-likes)
- `src/main/java/com/booktracker/shelf/ShelfService.java` — FRIEND_FINISHED_BOOK fan-out on READ transition
- `src/test/java/com/booktracker/security/JwtChannelInterceptorTest.java` — unit test: valid JWT sets UUID principal; invalid/absent → no principal
- `src/test/java/com/booktracker/notification/NotificationIntegrationTest.java` — four trigger integration tests
- `src/test/java/com/booktracker/shelf/ShelfServiceTest.java` — mocks added for FriendRequestRepository + NotificationService
- `pom.xml` — spring-boot-starter-websocket dependency

## Decisions Made

- JWT auth happens at the STOMP CONNECT layer, not the HTTP upgrade — SecurityConfig permits `/ws/**` without a token; the interceptor gate is STOMP-level
- `convertAndSendToUser` keyed by `recipient.getUsername()` which is the UUID string (not email) — matches the STOMP principal name set by the interceptor
- FRIEND_FINISHED_BOOK fan-out is synchronous inside the `updateMetadata` @Transactional method (not an async event); safe because `convertAndSendToUser` is a no-op for offline recipients
- NotificationService does NOT inject ShelfService (avoids a circular dependency); ShelfService injects NotificationService

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## Next Phase Readiness

- WebSocket backend is complete; frontend can now connect to `/ws` with a valid JWT and subscribe to `/user/queue/notifications`
- Plan 09-05 (frontend social UI) and Plan 09-06 (frontend notifications + WebSocket hook) can now proceed

---
*Phase: 09-user-discovery-friend-requests-real-time-notifications*
*Completed: 2026-07-05*
