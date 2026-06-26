/**
 * queryKeys.ts — Centralized TanStack Query key factory
 *
 * Shared by all pages/hooks to ensure consistent cache invalidation.
 * Usage: queryClient.invalidateQueries({ queryKey: QUERY_KEYS.shelf() })
 *
 * TanStack Query v5 note: invalidateQueries requires object form { queryKey: [...] }
 */
export const QUERY_KEYS = {
  shelf: (status?: string) => (status ? ['shelf', status] : ['shelf']) as const,
  book: (olKey: string) => ['book', olKey] as const,
  stats: () => ['stats'] as const,
  goal: () => ['goal'] as const,
  me: () => ['me'] as const,
  search: (q: string) => ['search', q] as const,
} as const;
