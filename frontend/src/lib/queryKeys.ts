/**
 * queryKeys.ts — Centralized TanStack Query key factory
 *
 * Shared by all pages/hooks to ensure consistent cache invalidation.
 * Usage: queryClient.invalidateQueries({ queryKey: QUERY_KEYS.shelf() })
 *
 * TanStack Query v5 note: invalidateQueries requires object form { queryKey: [...] }
 *
 * Return types are explicit `readonly string[]` — `as const` on ternary or computed
 * array expressions is not valid TypeScript (TS error TS1355). Explicit readonly satisfies
 * TanStack Query's QueryKey constraint without const-assertion syntax.
 */
export const QUERY_KEYS = {
  shelf: (status?: string): readonly string[] =>
    status ? ['shelf', status] : ['shelf'],
  book: (olKey: string): readonly string[] => ['book', olKey],
  stats: (): readonly string[] => ['stats'],
  goal: (): readonly string[] => ['goal'],
  me: (): readonly string[] => ['me'],
  search: (q: string): readonly string[] => ['search', q],
  // Social layer keys — include numeric page param so `readonly (string | number)[]`
  feed: (page?: number): readonly (string | number)[] =>
    page !== undefined ? ['feed', page] : ['feed'],
  profile: (userId: string, page?: number): readonly (string | number)[] =>
    page !== undefined ? ['profile', userId, page] : ['profile', userId],
  // Discovery & friend-request keys (Phase 9 — DISC-01/02/03)
  userSearch: (q: string): readonly string[] => ['userSearch', q],
  pendingReceived: (): readonly string[] => ['pendingReceived'],
  // Notification keys (Phase 9 — NOTIF-02/03)
  notifications: (): readonly string[] => ['notifications'],
  notificationsUnreadCount: (): readonly string[] => ['notifications', 'unread-count'],
} as const;
