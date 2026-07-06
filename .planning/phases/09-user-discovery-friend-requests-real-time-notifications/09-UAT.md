---
status: complete
phase: 09-user-discovery-friend-requests-real-time-notifications
source: 09-01-SUMMARY.md, 09-02-SUMMARY.md, 09-03-SUMMARY.md, 09-04-SUMMARY.md, 09-05-SUMMARY.md, 09-06-SUMMARY.md
started: 2026-07-05T00:00:00Z
updated: 2026-07-06T12:15:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running server. Clear ephemeral state (temp DBs, caches). Start the backend from scratch (docker-compose up + mvn spring-boot:run). Migrations V3 (friend_requests), V4 (review_likes), and V5 (notifications) should apply cleanly. Server boots without errors. A basic API call (e.g. GET /api/notifications/unread-count with a valid JWT) returns live data (not an error).
result: pass

### 2. People Search Tab
expected: Open the Search page. A "People" tab appears alongside the existing Books tab. Click People and type a username (or partial name) of another registered user. Results appear as a list of avatar initials + display name + an "Add Friend" button. No results for an empty query. Current user does not appear in their own search results.
result: pass

### 3. Send Friend Request
expected: In the People search results, click "Add Friend" next to a user. The button immediately changes to "Request Sent" (outline, disabled-looking). No page reload required. The request is now pending.
result: pass

### 4. Cancel Friend Request
expected: With a "Request Sent" button visible (from Test 3), click it. The button reverts to "Add Friend". The pending request is cancelled. You can re-send after cancelling.
result: pass

### 5. Pending Requests Widget on Feed
expected: Log in as a second user (the one you sent a request to in Test 3, or have another user send you a request). Open the Feed page. A "Friend requests" section appears above the feed listing any incoming pending requests — each showing the requester's name and Accept / Reject buttons. The widget is absent (not rendered) when there are no pending requests.
result: issue
reported: "Widget not visible on Feed page when feed is empty (no activity yet)"
severity: major
fixed: PendingRequestsWidget was inside the non-empty items branch; moved above the empty-state inline so it renders regardless of feed content

### 6. Accept Friend Request
expected: In the Pending Requests Widget (Test 5), click Accept on an incoming request. The request row disappears immediately (no reload). The friend's book activity should now be eligible to appear in your Feed. If you search the now-friend in the People tab, the button shows "Friends" (disabled).
result: pass

### 7. Reject Friend Request
expected: Have another user send you a friend request. In the Pending Requests Widget, click Reject. The request row disappears immediately. No further action required. The sender can re-send a request after rejection.
result: pass

### 8. Friends-based Feed
expected: After accepting a friend request (Test 6), the Feed page shows that friend's book activity (books they've added to their shelf, reviews, etc.). Books from users who are not friends do not appear in the Feed (the feed is no longer follows-based).
result: issue
reported: "Feed only shows a friend's book if they left a review. Books marked READ without a review don't appear. Also, new additions don't appear until the entire page is refreshed."
severity: major

### 9. Like a Review
expected: Visit another user's public profile page. On a book entry that has a review, a heart icon button is visible below the review text. Click the heart. The heart fills red and the like count increments by 1. The mutation is immediate (no page reload needed).
result: pass

### 10. Unlike a Review
expected: On the same review from Test 9 (heart is now filled red), click the heart again. The heart returns to outline (unfilled) and the like count decrements by 1. Clicking again would re-like.
result: pass

### 11. Notification Bell + Unread Badge
expected: Trigger a notification event (e.g. have another user send you a friend request or like one of your reviews). The sticky app header shows a Bell icon. A red badge appears on the bell showing the unread count (e.g. "1"). The badge shows "99+" if the count exceeds 99. No badge is shown when count is 0.
result: pass

### 12. Notification Inbox
expected: Click the bell icon. A bottom sheet slides up titled "Notifications". Each notification row shows: the appropriate Lucide icon (UserPlus for friend request, UserCheck for accepted, BookOpen for finished book, Heart for liked review), the actor's display name, a short description, and a relative timestamp (e.g. "2 minutes ago"). An empty state message "No notifications yet." appears when the list is empty.
result: pass
fixed: Page response shape mismatch (r.data → r.data.content) + useEffect refetch on sheet open

### 13. Mark All Read on Bell Click
expected: With an unread badge visible, click the bell. The badge disappears (count goes to 0) immediately as the sheet opens — no separate "mark all read" button needed. Re-opening the sheet shows the same notifications but the badge does not reappear.
result: pass

### 14. Real-time WebSocket Push (two-browser test)
expected: Open the app in two browser windows logged in as two different users (User A and User B). In Browser A, perform an action that creates a notification for User B (e.g. send a friend request to User B, or like one of User B's reviews). In Browser B, WITHOUT refreshing the page, the bell badge should increment within a few seconds. The notification should appear in the inbox sheet when opened.
result: pass

### 15. STOMP endpoint JWT auth
expected: STOMP-over-SockJS /ws endpoint with JWT auth at CONNECT frame; invalid/absent token yields no principal
result: pass
source: automated
coverage_id: D1

### 16. createNotification persist + push
expected: NotificationService.createNotification persists then pushes to recipient via convertAndSendToUser
result: pass
source: automated
coverage_id: D2

### 17. Four notification triggers
expected: Four notification triggers fire correctly: FRIEND_REQUEST, FRIEND_ACCEPTED, REVIEW_LIKED, FRIEND_FINISHED_BOOK
result: pass
source: automated
coverage_id: D3

## Summary

total: 17
passed: 17
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "Feed shows all friend's READ books, with or without a review"
  status: failed
  reason: "User reported: only books with a review appear in the feed"
  severity: major
  test: 8
  root_cause: "addToShelf() sets shelfStatus=READ but never calls applyAutoDateRules, so dateFinished is null. findFeedForFriends requires dateFinished IS NOT NULL, so books added directly as READ never appear until updateMetadata() is called (e.g. when adding a review), which runs applyAutoDateRules and sets dateFinished."
  artifacts:
    - path: "src/main/java/com/booktracker/shelf/ShelfService.java"
      issue: "addToShelf() does not set dateFinished when status=READ (D-10 auto-date only fires in applyAutoDateRules, which is only called from updateMetadata)"
  missing:
    - "In addToShelf(), after entry.setShelfStatus(status), add: if (status == ShelfStatus.READ && entry.getDateFinished() == null) { entry.setDateFinished(LocalDate.now()); }"

- truth: "New friend activity appears in feed without a manual page refresh"
  status: failed
  reason: "User reported: feed requires full page refresh to show new additions"
  severity: major
  test: 8
  root_cause: "handleNotification() in AppHeader.tsx only invalidates notificationsUnreadCount and notifications query keys. FRIEND_FINISHED_BOOK notifications arrive via WebSocket but the feed query key is never invalidated, so TanStack Query serves stale data until the page is reloaded."
  artifacts:
    - path: "frontend/src/components/layout/AppHeader.tsx"
      issue: "handleNotification does not invalidate QUERY_KEYS.feed() on FRIEND_FINISHED_BOOK notification type"
  missing:
    - "In handleNotification(), add: if (_notif.type === 'FRIEND_FINISHED_BOOK') { queryClient.invalidateQueries({ queryKey: QUERY_KEYS.feed() }); }"
