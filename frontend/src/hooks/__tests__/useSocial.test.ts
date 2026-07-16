/**
 * useSocial.test.ts — Wave 1 RED test for DISC-03 follow-count invalidation
 *
 * DISC-03: follow mutation must invalidate the profile query with exact:false so that
 * ALL pages of the profile cache (profile/u-target/0, profile/u-target/1, …) are
 * marked stale simultaneously. Without exact:false, only the exact key is invalidated
 * and paginated profile views show stale follower counts.
 *
 * This test will FAIL until Plan 03 fixes useFollowUser to pass { exact: false } to
 * invalidateQueries for the profile key. That is expected — this is the RED phase.
 *
 * Pattern: renderHook with a real QueryClient (no api mock needed — mutation is mocked
 * via MSW-style approach using vi.mocked api). Follows the same structure as
 * useNotifications.test.tsx (shared client, makeWrapper helper).
 */
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import type { ReactNode } from 'react';
import { useFollowUser } from '../useSocial';
import { QUERY_KEYS } from '../../lib/queryKeys';
import { api } from '../../lib/api';

vi.mock('../../lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), delete: vi.fn(), put: vi.fn() },
  TOKEN_KEY: 'booktracker_token',
}));

function makeClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: 0, staleTime: 0 },
      mutations: { retry: 0 },
    },
  });
}

function makeWrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      // @ts-expect-error — JSX in .ts file; vitest transforms this correctly
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (QueryClientProvider as any)({ client, children })
    );
  };
}

const MINIMAL_PROFILE = {
  userId: 'u-target',
  displayName: 'Alice',
  followerCount: 5,
  followingCount: 2,
  isFollowing: false,
  booksReadThisYear: 3,
  readEntries: {
    content: [],
    number: 0,
    size: 20,
    totalPages: 0,
    totalElements: 0,
  },
};

beforeEach(() => {
  vi.clearAllMocks();
  // Mock POST /api/users/u-target/follow → { following: true } with 201
  vi.mocked(api.post).mockResolvedValue({ data: { following: true }, status: 201 } as never);
  // Mock GET /api/users/u-target/profile → minimal public profile
  vi.mocked(api.get).mockResolvedValue({ data: MINIMAL_PROFILE } as never);
});

describe('useFollowUser — DISC-03 profile query invalidation', () => {
  /**
   * DISC-03: After calling mutate(), the profile query for 'u-target' must be
   * invalidated (isInvalidated: true) regardless of which page was cached.
   *
   * The fix in Plan 03: pass { exact: false } so ALL paginated profile keys
   * (profile/u-target/0, profile/u-target/1, …) are invalidated in one call.
   *
   * This test is RED until Plan 03 applies the fix.
   */
  it('follow mutation invalidates profile query so counts update immediately (DISC-03)', async () => {
    const client = makeClient();
    const wrapper = makeWrapper(client);

    // Pre-populate the cache with page 0 of the target's profile
    // (simulates what happens when the user has already visited the profile page)
    client.setQueryData(QUERY_KEYS.profile('u-target', 0), MINIMAL_PROFILE);

    // Verify the cache is currently valid (not invalidated)
    const stateBefore = client.getQueryState(QUERY_KEYS.profile('u-target', 0));
    expect(stateBefore?.isInvalidated).toBe(false);

    // Render the hook with the pre-populated client
    const { result } = renderHook(() => useFollowUser('u-target'), { wrapper });

    // Fire the follow mutation
    result.current.mutate();

    // Wait for the mutation to settle (success path)
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    // DISC-03 assertion: the profile query must be invalidated after mutation success.
    // With exact:false, QUERY_KEYS.profile('u-target') matches the paginated key
    // ['profile', 'u-target', 0] so isInvalidated flips to true.
    //
    // Without exact:false (current implementation), only exact key ['profile','u-target']
    // is searched — the paginated key ['profile','u-target',0] is NOT matched →
    // isInvalidated remains false → this test FAILS (RED phase expected).
    const stateAfter = client.getQueryState(QUERY_KEYS.profile('u-target', 0));
    expect(stateAfter?.isInvalidated).toBe(true);
  });
});
