---
phase: 09-user-discovery-friend-requests-real-time-notifications
verified: 2026-07-06T18:00:00Z
status: passed
score: 17/17 UAT tests passed
behavior_unverified: 0
overrides_applied: 0
re_verification: null
threats_open: 0
---

# Phase 09: User Discovery, Friend Requests & Real-time Notifications — Verification Report

**Phase Goal:** Users can find other readers, send and manage friend requests, react to reviews, and receive real-time notifications for social events
**Verified:** 2026-07-06
**Status:** PASSED
**Re-verification:** No — initial verification (two tests re-run after inline fixes in commit 9180c16)

---

## Goal Achievement

### Roadmap Success Criteria

Six success criteria from ROADMAP.md, all verified:

| # | Success Criterion | Status | Evidence |
|---|-------------------|--------|---------|
| 1 | A user can search for other users by username or display name and see results without touching the console | VERIFIED | `UserController.searchUsers` endpoint with `@RequestParam q`; `UserRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase`; excludes self. UAT test 2 passed. |
| 2 | A user can send a friend request to any other user; the recipient can accept or reject it; sender can cancel a pending request | VERIFIED | `FriendRequestService` implements send/accept/reject/cancel with `saveAndFlush`-catch-409 guard and `FriendRequestStatus` enum. UAT tests 3 + 4 + 6 + 7 all passed. |
| 3 | A user can discover and send friend requests directly from the Feed page | VERIFIED | `PendingRequestsWidget` renders above feed content (and above empty-state); Fixed in 9180c16 to render regardless of feed being empty. UAT test 5 re-verified pass. |
| 4 | A user can like a review on another user's public profile | VERIFIED | `LikeService.like/unlike` with `saveAndFlush`-catch-409 duplicate guard; `likeCount` + `likedByMe` returned in `PublicShelfEntryDto`. UAT tests 9 + 10 passed. |
| 5 | A user receives a real-time WebSocket notification for: incoming friend request, accepted friend request, a friend finishing a book, a like on their review | VERIFIED | Four triggers in `FriendRequestService`, `LikeService`, and `ShelfService` call `notificationService.createNotification`; `NotificationService` persists then pushes via `SimpMessagingTemplate.convertAndSendToUser`. UAT test 14 passed (two-browser real-time push). |
| 6 | The notification bell in the nav shows an unread badge count; clicking it opens a notification inbox that marks all as read | VERIFIED | `AppHeader` polls `GET /api/notifications/unread-count`; badge renders `99+` for overflow; `NotificationSheet` calls `markAllRead` on bell click; `useWebSocket` invalidates count on push. UAT tests 11 + 12 + 13 passed. |

---

### UAT Results (17/17 passed)

| # | Test | Result | Notes |
|---|------|--------|-------|
| 1 | Cold Start Smoke Test | PASS | V3/V4/V5 migrations apply cleanly; server boots; API responds |
| 2 | People Search Tab | PASS | People tab appears; user search returns results; self excluded |
| 3 | Send Friend Request | PASS | "Add Friend" → "Request Sent" immediately (optimistic); no reload |
| 4 | Cancel Friend Request | PASS | "Request Sent" → "Add Friend" on click; request cancelled; re-send works |
| 5 | Pending Requests Widget on Feed | PASS | Fixed: widget moved above empty-state branch; renders with zero feed items |
| 6 | Accept Friend Request | PASS | Request row disappears immediately; friend search shows "Friends" |
| 7 | Reject Friend Request | PASS | Request row disappears immediately; sender can re-send |
| 8 | Friends-based Feed | PASS | Fixed: `addToShelf` sets `dateFinished` on READ; feed invalidated on `FRIEND_FINISHED_BOOK` push |
| 9 | Like a Review | PASS | Heart fills red; count increments; immediate (no reload) |
| 10 | Unlike a Review | PASS | Heart reverts to outline; count decrements |
| 11 | Notification Bell + Unread Badge | PASS | Badge appears on new event; `99+` overflow works; absent at 0 |
| 12 | Notification Inbox | PASS | Sheet shows icon + actor + description + relative time; empty state present |
| 13 | Mark All Read on Bell Click | PASS | Badge clears on bell click; does not reappear on re-open |
| 14 | Real-time WebSocket Push | PASS | Badge increments in second browser within seconds; no refresh needed |
| 15 | STOMP endpoint JWT auth | PASS (automated) | `JwtChannelInterceptor` sets principal from CONNECT frame JWT |
| 16 | createNotification persist + push | PASS (automated) | `NotificationService.createNotification` persists then pushes via `convertAndSendToUser` |
| 17 | Four notification triggers | PASS (automated) | FRIEND_REQUEST, FRIEND_ACCEPTED, REVIEW_LIKED, FRIEND_FINISHED_BOOK all fire |

---

### Requirements Coverage

| Requirement | Plan(s) | Status | Evidence |
|------------|---------|--------|---------|
| DISC-01 | 09-01, 09-05 | SATISFIED | `GET /api/users/search?q=` + People tab in SearchPage; `UserRepository` full-text ignore-case; excludes self |
| DISC-02 | 09-01, 09-05 | SATISFIED | `FriendRequestController` — send/accept/reject/cancel endpoints; `FriendRequestButton` 4-state component |
| DISC-03 | 09-01, 09-05 | SATISFIED | `PendingRequestsWidget` on FeedPage; re-fixed to render above empty-state (9180c16) |
| DISC-04 | 09-02, 09-05 | SATISFIED | `LikeController` — like/unlike; `likeCount` + `likedByMe` in `PublicShelfEntryDto`; heart button in UserPublicProfilePage |
| NOTIF-01 | 09-03, 09-04 | SATISFIED | `NotificationEntity` + V5 migration; four triggers persist notifications; `NotificationRepository` stores all types |
| NOTIF-02 | 09-04, 09-06 | SATISFIED | `WebSocketConfig` STOMP/SockJS; `JwtChannelInterceptor`; `convertAndSendToUser`; `useWebSocket` STOMP client hook |
| NOTIF-03 | 09-03, 09-06 | SATISFIED | `GET /api/notifications/unread-count`; bell badge + `99+` overflow; `NotificationSheet` inbox; mark-all-read on open |

---

### Inline Fixes Applied During UAT (all re-verified)

| Issue | Root Cause | Fix (commit 9180c16) | Re-verified |
|-------|-----------|----------------------|-------------|
| PendingRequestsWidget invisible on empty feed | Widget rendered inside non-empty items branch | Moved above empty-state check | ✓ |
| Friends' READ books missing from feed | `addToShelf()` never set `dateFinished`; `findFeedForFriends` requires `dateFinished IS NOT NULL` | `addToShelf()` sets `dateFinished = LocalDate.now()` when status = READ | ✓ |
| Feed doesn't update on real-time push | `handleNotification()` didn't invalidate feed query on `FRIEND_FINISHED_BOOK` | Added `queryClient.invalidateQueries(QUERY_KEYS.feed())` for that type | ✓ |

---

### Key Production Artifacts

| Artifact | Status |
|----------|--------|
| `V3__friend_requests.sql` | VERIFIED — bidirectional unique constraint, status CHECK |
| `V4__review_likes.sql` | VERIFIED — unique (user_id, entry_id) constraint |
| `V5__notifications.sql` | VERIFIED — type CHECK matches `NotificationType` enum values |
| `FriendRequestEntity` + `FriendRequestRepository` | VERIFIED — JPQL OR subquery for bidirectional friends lookup |
| `FriendRequestService` + `FriendRequestController` | VERIFIED — send/accept/reject/cancel with ownership guard |
| `LikeEntity` + `LikeRepository` + `LikeService` + `LikeController` | VERIFIED — idempotent like/unlike, IDOR prevention |
| `NotificationEntity` + `NotificationRepository` + `NotificationService` | VERIFIED — persist + push via `SimpMessagingTemplate`; JOIN FETCH actor |
| `NotificationController` | VERIFIED — unread-count, paginated list, mark-all-read (user-scoped) |
| `WebSocketConfig` + `JwtChannelInterceptor` | VERIFIED — STOMP/SockJS; JWT auth at CONNECT; principal = UUID string |
| `FriendRequestButton.tsx` | VERIFIED — 4-state (NONE/PENDING_SENT/PENDING_RECEIVED/ACCEPTED) |
| `PendingRequestsWidget.tsx` | VERIFIED — renders above empty-state; null-return when no pending requests |
| `AppHeader.tsx` + `NotificationSheet.tsx` | VERIFIED — bell badge; bottom-sheet inbox; mark-all-read on open |
| `useWebSocket.ts` + `useNotifications.ts` | VERIFIED — STOMP lifecycle; lazy notification list; real-time badge invalidation |

---

### Summary Table

| Check | Result |
|-------|--------|
| All 17 UAT tests pass | PASS |
| 3 inline issues found and re-verified fixed | PASS |
| All 6 roadmap success criteria satisfied | PASS |
| DISC-01 through DISC-04 satisfied | PASS |
| NOTIF-01 through NOTIF-03 satisfied | PASS |
| V3/V4/V5 migrations apply cleanly on cold start | PASS |
| Real-time WebSocket push verified (two-browser test) | PASS |
| Automated coverage: STOMP auth, createNotification, four triggers | PASS |

---

**Overall verdict: Phase 09 goal is ACHIEVED. All 17 UAT tests passed (3 issues found and re-verified fixed inline). DISC-01 through DISC-04 and NOTIF-01 through NOTIF-03 are satisfied.**

---

_Verified: 2026-07-06_
_Verifier: Claude (gsd-verify-work)_
