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
} as const;
