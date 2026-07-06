/**
 * useSocial.ts — TanStack Query hooks for the social layer (Phase 8)
 *
 * SOCIAL-01: useFollowUser / useUnfollowUser — follow toggle mutations
 * SOCIAL-02: usePublicProfile — public profile query (READ entries only)
 * SOCIAL-03: useFeed — infinite-scroll activity feed of followed users' finishes
 *
 * Security notes:
 * - T-08F-02: follower identity comes from the JWT interceptor, never the request body
 * - T-08F-03: Follow button visibility is the caller's responsibility (hide for own profile)
 *
 * TanStack Query v5 notes:
 * - Use isPending (not isLoading) for initial fetch state
 * - invalidateQueries requires object form: { queryKey: [...] }
 * - useInfiniteQuery requires initialPageParam and getNextPageParam
 */
import {
  useInfiniteQuery,
  useQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../lib/api';
import { QUERY_KEYS } from '../lib/queryKeys';
import type {
  Page,
  FeedItem,
  PublicProfile,
  FollowStatus,
  FriendRequest,
  UserSearchResult,
} from '../types/api.types';

// ────────────────────────────────────────────────────────
// SOCIAL-03: Activity feed (infinite scroll, Spring Page shape)
// Mirrors useShelfList exactly: initialPageParam 0, getNextPageParam via number/totalPages
// ────────────────────────────────────────────────────────

/**
 * useFeed — infinite query for GET /feed?page=N&size=20
 *
 * Flattened: data.pages.flatMap(p => p.content) gives the full feed list.
 * Keyed by QUERY_KEYS.feed() — invalidated when the user follows/unfollows.
 */
export function useFeed() {
  return useInfiniteQuery({
    queryKey: QUERY_KEYS.feed(),
    queryFn: ({ pageParam }) =>
      api
        .get<Page<FeedItem>>('/feed', { params: { page: pageParam, size: 20 } })
        .then((r) => r.data),
    initialPageParam: 0,
    getNextPageParam: (lastPage: Page<FeedItem>) =>
      lastPage.number < lastPage.totalPages - 1
        ? lastPage.number + 1
        : undefined,
  });
}

// ────────────────────────────────────────────────────────
// SOCIAL-02: Public profile (single page of READ entries)
// ────────────────────────────────────────────────────────

/**
 * usePublicProfile — query for GET /users/:userId/profile?page=N&size=20
 *
 * Includes READ entries only (backend enforces; WANT_TO_READ/CURRENTLY_READING hidden).
 * enabled: !!userId prevents firing with an empty string from useParams().
 */
export function usePublicProfile(userId: string, page = 0) {
  return useQuery({
    queryKey: QUERY_KEYS.profile(userId, page),
    queryFn: () =>
      api
        .get<PublicProfile>(`/users/${userId}/profile`, { params: { page, size: 20 } })
        .then((r) => r.data),
    enabled: !!userId,
  });
}

// ────────────────────────────────────────────────────────
// Current user identity (used to hide Follow button on own profile — Pitfall 5)
// ────────────────────────────────────────────────────────

/**
 * useCurrentUserId — query for GET /users/me → extracts the id field.
 *
 * Keyed by QUERY_KEYS.me() — already in cache if the profile page was previously loaded.
 * Used in UserPublicProfilePage to compare against profile.userId (RESEARCH Pitfall 5).
 */
export function useCurrentUserId() {
  return useQuery({
    queryKey: QUERY_KEYS.me(),
    queryFn: () =>
      api.get<{ id: string }>('/users/me').then((r) => r.data.id),
  });
}

// ────────────────────────────────────────────────────────
// SOCIAL-01: Follow / Unfollow mutations
// ────────────────────────────────────────────────────────

/**
 * useFollowUser — mutation for POST /users/:userId/follow
 *
 * onSuccess: invalidates profile (isFollowing changed) and feed (new followee content).
 * T-08F-02: follower identity is the JWT-authenticated user, never in the request body.
 */
export function useFollowUser(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api.post<FollowStatus>(`/users/${userId}/follow`).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.profile(userId) });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.feed() });
    },
  });
}

/**
 * useUnfollowUser — mutation for DELETE /users/:userId/follow
 *
 * onSuccess: invalidates the same two keys as useFollowUser so UI reflects the new state.
 */
export function useUnfollowUser(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.delete(`/users/${userId}/follow`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.profile(userId) });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.feed() });
    },
  });
}

// ────────────────────────────────────────────────────────
// DISC-01: User search (people tab)
// ────────────────────────────────────────────────────────

/**
 * useUserSearch — query for GET /api/users/search?q=
 *
 * Returns Page<UserSearchResult> (sorted by displayName ASC, excludes self).
 * enabled: !!query prevents firing with an empty string.
 *
 * T-09-04: query param is JPQL bind-parameter on the backend — no injection risk.
 */
export function useUserSearch(query: string) {
  return useQuery({
    queryKey: QUERY_KEYS.userSearch(query),
    queryFn: () =>
      api
        .get<Page<UserSearchResult>>('/users/search', { params: { q: query } })
        .then((r) => r.data),
    enabled: !!query,
  });
}

// ────────────────────────────────────────────────────────
// DISC-02/03: Friend request mutations
// ────────────────────────────────────────────────────────

/**
 * useSendFriendRequest — mutation for POST /api/friend-requests
 *
 * T-09-17: Actor identity comes from the JWT interceptor — request body only carries recipientId.
 * onSuccess: invalidates userSearch (friendStatus updated) and feed key prefix.
 */
export function useSendFriendRequest(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api
        .post<FriendRequest>('/friend-requests', { recipientId: userId })
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['userSearch'] });
    },
    onError: () => {
      // Refresh search so stale friendStatus (e.g. after a race or rejected request) is corrected.
      queryClient.invalidateQueries({ queryKey: ['userSearch'] });
    },
  });
}

/**
 * useAcceptFriendRequest — mutation for PUT /api/friend-requests/{requestId}/accept
 *
 * onSuccess: invalidates pendingReceived (item removed), userSearch (status updated), and feed
 *            (newly accepted friend's finishes appear in feed).
 */
export function useAcceptFriendRequest(requestId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api
        .put<FriendRequest>(`/friend-requests/${requestId}/accept`)
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.pendingReceived() });
      queryClient.invalidateQueries({ queryKey: ['userSearch'] });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.feed() });
    },
  });
}

/**
 * useRejectFriendRequest — mutation for PUT /api/friend-requests/{requestId}/reject
 *
 * onSuccess: invalidates pendingReceived (item removed) and userSearch (status updated).
 */
export function useRejectFriendRequest(requestId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api
        .put<FriendRequest>(`/friend-requests/${requestId}/reject`)
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.pendingReceived() });
      queryClient.invalidateQueries({ queryKey: ['userSearch'] });
    },
  });
}

/**
 * useCancelFriendRequest — mutation for DELETE /api/friend-requests/{requestId}
 *
 * Cancels an outgoing PENDING request (requester's action — deletes the row).
 * onSuccess: invalidates userSearch so the friend status reverts to NONE.
 */
export function useCancelFriendRequest(requestId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.delete(`/friend-requests/${requestId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['userSearch'] });
    },
    onError: () => {
      // Request may have been rejected/deleted by the other user — refresh search to show real state.
      queryClient.invalidateQueries({ queryKey: ['userSearch'] });
    },
  });
}

/**
 * usePendingReceivedRequests — query for GET /api/friend-requests/pending-received
 *
 * Returns FriendRequest[] (PENDING status, ordered by createdAt DESC).
 * Used by PendingRequestsWidget on FeedPage (DISC-03, D-09).
 */
export function usePendingReceivedRequests() {
  return useQuery({
    queryKey: QUERY_KEYS.pendingReceived(),
    queryFn: () =>
      api
        .get<FriendRequest[]>('/friend-requests/pending-received')
        .then((r) => r.data),
  });
}

// ────────────────────────────────────────────────────────
// DISC-04: Review like / unlike mutations
// ────────────────────────────────────────────────────────

/**
 * useLikeReview — mutation for POST /api/entries/{entryId}/like
 *
 * T-09-17: Liker identity comes from JWT — entryId is path, no userId in body.
 * onSuccess: invalidates the profile query so likeCount + likedByMe update.
 */
export function useLikeReview(entryId: string, profileUserId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post(`/entries/${entryId}/like`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.profile(profileUserId) });
    },
  });
}

/**
 * useUnlikeReview — mutation for DELETE /api/entries/{entryId}/like
 *
 * onSuccess: invalidates the profile query so likeCount + likedByMe update.
 */
export function useUnlikeReview(entryId: string, profileUserId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.delete(`/entries/${entryId}/like`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.profile(profileUserId) });
    },
  });
}
