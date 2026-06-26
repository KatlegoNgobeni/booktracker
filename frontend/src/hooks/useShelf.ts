/**
 * useShelf.ts — TanStack Query hooks for shelf list, entry detail, and shelf mutations
 *
 * D-09: useShelfList uses useInfiniteQuery with Spring Page<ShelfEntry> shape
 * D-10: progress bar logic lives in ShelfPage (pageCount opt-in field on ShelfEntry)
 * D-11: ShelfEntryEditorPage uses useUpdateShelfMetadata + useUpdateProgress (split endpoints)
 * T-06-08: useShelfEntry surfaces 403 ownership errors to the caller — no retry on 403
 * T-06-09: useUpdateShelfMetadata body excludes currentPage (mass-assignment guard)
 *
 * TanStack Query v5 notes:
 * - Use isPending (not isLoading) for initial fetch state
 * - invalidateQueries requires object form: { queryKey: [...] }
 */
import {
  useInfiniteQuery,
  useQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { api } from '../lib/api';
import { QUERY_KEYS } from '../lib/queryKeys';
import type { ShelfEntry, ShelfStatus, Page } from '../types/api.types';

// ────────────────────────────────────────────────────────
// D-09 — List hooks (paginated by status)
// ────────────────────────────────────────────────────────

/**
 * useShelfList — infinite query for GET /shelf?status=<STATUS>
 *
 * Uses real Spring Page shape: { content, number, totalPages, ... }
 * getNextPageParam uses number/totalPages (NOT array length).
 */
export function useShelfList(status: ShelfStatus) {
  return useInfiniteQuery({
    queryKey: QUERY_KEYS.shelf(status),
    queryFn: ({ pageParam }) =>
      api
        .get<Page<ShelfEntry>>('/shelf', {
          params: { status, page: pageParam, size: 20 },
        })
        .then((r) => r.data),
    initialPageParam: 0,
    getNextPageParam: (lastPage: Page<ShelfEntry>) =>
      lastPage.number < lastPage.totalPages - 1
        ? lastPage.number + 1
        : undefined,
  });
}

// ────────────────────────────────────────────────────────
// D-11 — Single entry + mutation hooks
// ────────────────────────────────────────────────────────

/**
 * useShelfEntry — query for GET /shelf/:id
 *
 * Uses a distinct key ['shelf', 'entry', id] to allow targeted invalidation
 * without blowing away the list cache on every save.
 *
 * T-06-08: 403 is not retried — surfaced as error for the caller to render
 * access-denied UI.
 */
export function useShelfEntry(id: string) {
  return useQuery({
    queryKey: ['shelf', 'entry', id] as const,
    queryFn: () => api.get<ShelfEntry>('/shelf/' + id).then((r) => r.data),
    enabled: !!id,
    retry: (failureCount, error: unknown) => {
      const status = (error as { response?: { status?: number } })?.response
        ?.status;
      if (status === 403 || status === 404) return false;
      return failureCount < 1;
    },
  });
}

/**
 * useUpdateShelfMetadata — PATCH /shelf/:id
 *
 * Body: { status, rating, review, dateStarted, dateFinished }
 * CRITICAL: currentPage is EXCLUDED (T-06-09 mass-assignment guard).
 * Progress is updated via useUpdateProgress → /shelf/:id/progress.
 */
export function useUpdateShelfMetadata(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      status: ShelfStatus;
      rating: number | null;
      review: string | null;
      dateStarted: string | null;
      dateFinished: string | null;
    }) => api.patch<ShelfEntry>('/shelf/' + id, body).then((r) => r.data),
    onSuccess: () => {
      // Invalidate all shelf list queries (all statuses) + this entry's detail
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.shelf() });
      queryClient.invalidateQueries({ queryKey: ['shelf', 'entry', id] });
    },
  });
}

/**
 * useUpdateProgress — PATCH /shelf/:id/progress
 *
 * Body: { currentPage } only. Backend UpdateProgressRequest requires NotNull, Min 0.
 * Kept as a separate mutation to enforce the two-endpoint split (D-04).
 */
export function useUpdateProgress(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { currentPage: number }) =>
      api
        .patch<ShelfEntry>('/shelf/' + id + '/progress', body)
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.shelf() });
      queryClient.invalidateQueries({ queryKey: ['shelf', 'entry', id] });
    },
  });
}

/**
 * useRemoveShelfEntry — DELETE /shelf/:id
 *
 * On success: invalidate all shelf list queries (entry is gone).
 * Caller navigates to /shelf after confirmation dialog.
 */
export function useRemoveShelfEntry(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.delete('/shelf/' + id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.shelf() });
    },
  });
}
