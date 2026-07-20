/**
 * useCurrentUser.ts — shared identity hook on GET /users/me (AVATAR-06)
 *
 * Single TanStack Query hook for the signed-in user's identity, shared by
 * AppHeader (avatar + display name) and ProfilePage (identity card). Both
 * consumers read the same QUERY_KEYS.me() cache entry, so the app makes one
 * network call for identity regardless of how many surfaces render it.
 *
 * Infinite staleTime — core identity (id, email, displayName) is immutable; photoUrl
 * updates after upload/remove via invalidateQueries. This is safe ONLY because clearAuthSession
 * (lib/auth.ts) wipes the query cache at every auth boundary: voluntary
 * sign-out (ProfilePage), 401 forced logout (api.ts interceptor), and
 * defensively on login/register success (LoginPage/RegisterPage).
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
  photoUrl: string | null;
}

export function useCurrentUser() {
  return useQuery({
    queryKey: QUERY_KEYS.me(),
    queryFn: () => api.get<UserMe>('/users/me').then((r) => r.data),
    staleTime: Infinity,
  });
}
