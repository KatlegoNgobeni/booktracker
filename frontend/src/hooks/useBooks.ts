/**
 * useBooks.ts — TanStack Query hooks for book search, detail, and shelf mutations
 *
 * D-13: debounced search uses useInfiniteQuery with array-based pagination (no Page wrapper)
 * D-14: Load more via fetchNextPage — getNextPageParam returns next index when lastPage.length === 10
 * D-15: useAddToShelf posts { olKey, status } and invalidates shelf cache
 * D-16: useShelfEntryForBook checks shelf cache to prevent a 409 duplicate add
 *
 * TanStack Query v5 notes:
 * - Use isPending (not isLoading) for initial fetch
 * - invalidateQueries requires object form { queryKey: [...] }
 * - getNextPageParam receives (lastPage, allPages)
 */
import { useInfiniteQuery, useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { QUERY_KEYS } from '../lib/queryKeys';
import type { BookSearchResult, BookDetail, ShelfStatus, ShelfEntry, Page } from '../types/api.types';

/**
 * useBookSearch — infinite query for GET /books/search?q=...
 *
 * CRITICAL: The search endpoint returns a PLAIN ARRAY (not a Spring Page).
 * Pagination is index-based: page 0 = first 10 results, page 1 = next 10, etc.
 * getNextPageParam returns the next index only when lastPage.length === 10.
 */
export function useBookSearch(query: string) {
  return useInfiniteQuery({
    queryKey: QUERY_KEYS.search(query),
    queryFn: ({ pageParam }) =>
      api
        .get<BookSearchResult[]>('/books/search', {
          params: { q: query, page: pageParam, size: 10 },
        })
        .then((r) => r.data),
    initialPageParam: 0,
    getNextPageParam: (lastPage: BookSearchResult[], allPages: BookSearchResult[][]) =>
      lastPage.length === 10 ? allPages.length : undefined,
    enabled: query.trim().length > 0,
  });
}

/**
 * useBookDetail — query for GET /books/:olKey
 * Note: first fetch may be slow while backend cache-or-fetches from Open Library
 */
export function useBookDetail(olKey: string) {
  return useQuery({
    queryKey: QUERY_KEYS.book(olKey),
    queryFn: () => api.get<BookDetail>('/books/' + olKey).then((r) => r.data),
    enabled: !!olKey,
  });
}

/**
 * useAddToShelf — mutation for POST /shelf
 * On success: invalidates all shelf queries so shelf list + already-on-shelf state refresh (D-16)
 */
export function useAddToShelf() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ olKey, status }: { olKey: string; status: ShelfStatus }) =>
      api.post<ShelfEntry>('/shelf', { olKey, status }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.shelf() });
    },
  });
}

/**
 * useShelfEntryForBook — D-16: check if book is already on user's shelf
 *
 * Reads from the TanStack Query cache (all shelf-prefixed queries). Handles both:
 * - useQuery results: Page<ShelfEntry>
 * - useInfiniteQuery results: InfiniteData<Page<ShelfEntry>>
 *
 * Returns the matching ShelfEntry or undefined. Non-reactive for cache misses —
 * cache is warm after user visits the Shelf page. Refreshes reactively after
 * useAddToShelf invalidates shelf queries.
 */
export function useShelfEntryForBook(olKey: string): ShelfEntry | undefined {
  const queryClient = useQueryClient();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const cachedQueries = queryClient.getQueriesData<any>({ queryKey: ['shelf'] });
  for (const [, data] of cachedQueries) {
    if (!data) continue;
    // Handle InfiniteData<Page<T>> (useInfiniteQuery) and Page<T> (useQuery)
    const pages: Array<Page<ShelfEntry>> = data.pages ? data.pages : [data];
    for (const page of pages) {
      const content: ShelfEntry[] = page?.content ?? [];
      const found = content.find((e) => e.olKey === olKey);
      if (found) return found;
    }
  }
  return undefined;
}
