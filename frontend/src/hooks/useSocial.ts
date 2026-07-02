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
import type { Page, FeedItem, PublicProfile, FollowStatus } from '../types/api.types';

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
