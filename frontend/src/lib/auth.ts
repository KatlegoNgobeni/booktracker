/**
 * auth.ts — centralized auth-boundary session teardown (AVATAR-06, 12-07)
 *
 * Single source of truth for tearing down a session at every auth exit.
 * Fixes UAT tests 3 and 4 (Phase 12): the module-scope QueryClient survived
 * sign-out/sign-in, so the previous user's ['me'] identity (staleTime:
 * Infinity) and ['notifications','unread-count'] entries were served to the
 * next user in the same tab until a hard reload.
 *
 * Call sites (after plan 12-08 wires the remaining two):
 * 1. api.ts 401 response interceptor (forced logout) — wired in 12-07
 * 2. ProfilePage handleSignOut (voluntary sign-out) — 12-08
 * 3. LoginPage/RegisterPage defensive clear on successful sign-in — 12-08
 *
 * Import-graph rule: this module imports NO local modules. The QueryClient
 * arrives as a parameter so React components can pass useQueryClient()
 * (test-injectable) while api.ts passes the lib/queryClient.ts singleton.
 */
import type { QueryClient } from '@tanstack/react-query';

export const TOKEN_KEY = 'booktracker_token';

/**
 * Tear down the current auth session:
 * (a) remove the JWT from localStorage       — synchronous
 * (b) clear ALL cached queries on `client`   — synchronous
 * (c) purge the SW 'api-cache' Cache Storage — awaited, rejection swallowed
 *
 * (a) and (b) completing before the first await is load-bearing: SPA callers
 * fire-and-forget this helper, so token + in-memory cache must be gone before
 * the next microtask runs.
 *
 * WR-03 semantics: 'api-cache' holds authenticated API responses and must not
 * outlive the session. 'dicebear-avatars' is deliberately left alone (12-04)
 * — avatars are public, non-sensitive, and keyed by userId.
 */
export async function clearAuthSession(client: QueryClient): Promise<void> {
  localStorage.removeItem(TOKEN_KEY);
  client.clear();
  const cacheStorage = (globalThis as { caches?: CacheStorage }).caches;
  if (cacheStorage) {
    await cacheStorage.delete('api-cache').catch(() => {});
  }
}
