/**
 * useStats.ts — TanStack Query hooks for stats and goal (UI-01)
 *
 * useStats()    — GET /stats → StatsDto (cached 30s)
 * useGoal()     — GET /goal → GoalDto | null (404 = no goal set)
 * useSetGoal()  — PUT /goal { targetCount } → GoalDto; invalidates goal + stats
 *
 * TanStack Query v5 notes:
 * - Use isPending (not isLoading) for initial fetch state
 * - invalidateQueries requires object form: { queryKey: [...] }
 * - onSuccess/onError removed from useQuery in v5; mutations use them via useMutation
 *
 * T-06-13: useGoal maps a 404 to null (no error thrown) — "no goal" is a valid state
 * T-06-14: useSetGoal coerces targetCount to a number before PUT (GoalDto: non-negative integer)
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { QUERY_KEYS } from '../lib/queryKeys';
import type { StatsDto, GoalDto } from '../types/api.types';

/**
 * useStats — query for GET /stats
 * Returns a StatsDto with always-present booksPerMonth (12-element array).
 * Optional fields (averageRating, longestBook, etc.) may be absent — use optional chaining.
 */
export function useStats() {
  return useQuery({
    queryKey: QUERY_KEYS.stats(),
    queryFn: () => api.get<StatsDto>('/stats').then((r) => r.data),
  });
}

/**
 * useGoal — query for GET /goal
 * Maps a 404 (no goal set for this year) to null — not an error.
 * retry:false prevents TanStack from retrying 404s.
 */
export function useGoal() {
  return useQuery<GoalDto | null>({
    queryKey: QUERY_KEYS.goal(),
    queryFn: async () => {
      try {
        const r = await api.get<GoalDto>('/goal');
        return r.data;
      } catch (err: unknown) {
        const status = (err as { response?: { status?: number } })?.response?.status;
        if (status === 404) return null;
        throw err;
      }
    },
    retry: false,
  });
}

/**
 * useSetGoal — mutation for PUT /goal { targetCount }
 * On success: invalidates goal + stats queries so both refresh.
 * T-06-14: targetCount is coerced to integer to match backend GoalRequest validation.
 */
export function useSetGoal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (targetCount: number) =>
      api.put<GoalDto>('/goal', { targetCount: Math.floor(targetCount) }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.goal() });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.stats() });
    },
  });
}
