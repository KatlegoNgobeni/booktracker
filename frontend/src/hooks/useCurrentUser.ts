/**
 * useCurrentUser.ts — shared identity hook on GET /users/me (AVATAR-06)
 *
 * Single TanStack Query hook for the signed-in user's identity, shared by
 * AppHeader (avatar + display name) and ProfilePage (identity card). Both
 * consumers read the same QUERY_KEYS.me() cache entry, so the app makes one
 * network call for identity regardless of how many surfaces render it.
 *
 * Infinite staleTime — identity (id, email, displayName) is immutable in MVP
 * (D-08: no profile editing endpoint exists), so the cached value never goes
 * stale within a session. Sign-out clears the QueryClient on navigation.
 *
 * T-06-12: only the authenticated user's own /users/me is reachable — the
 * server scopes the response to the JWT subject.
 */
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import { QUERY_KEYS } from '../lib/queryKeys';

/** Response shape of GET /users/me (moved here from ProfilePage). */
export interface UserMe {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
}

export function useCurrentUser() {
  return useQuery({
    queryKey: QUERY_KEYS.me(),
    queryFn: () => api.get<UserMe>('/users/me').then((r) => r.data),
    staleTime: Infinity,
  });
}
