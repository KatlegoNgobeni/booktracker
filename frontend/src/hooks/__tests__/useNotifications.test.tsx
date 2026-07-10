/**
 * useNotifications.test.tsx — UAT test 4 regression: unread-count vs the auth boundary
 *
 * Bug mechanism (see .planning/debug/missing-unread-badge-on-signin.md):
 * the unread-count query is cached with the production default staleTime of
 * 5 minutes. If user X's fresh-cached 0 survives sign-out, user Y's bell badge
 * silently serves that 0 instead of refetching — notifications received while
 * signed out never surface until the cache goes stale or the tab reloads.
 *
 * Two tests pin the fix from both sides:
 * 1. WITH clearAuthSession between mounts → the second mount refetches (fix).
 * 2. WITHOUT it (control) → the second mount serves the stale cached 0 (bug).
 *    Reverting the teardown turns test 1 red while this control documents why.
 *
 * clearAuthSession from the REAL lib/auth.ts runs here — intentional, so the
 * assertions exercise the true production teardown (it only touches
 * localStorage, the injected client, and an absent CacheStorage under jsdom).
 *
 * Scope: useUnreadCount only — useNotifications (enabled:false inbox list) and
 * useMarkAllRead are out of scope for this gap.
 */
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import type { ReactNode } from 'react';
import { useUnreadCount } from '../useNotifications';
import { clearAuthSession } from '../../lib/auth';
import { api } from '../../lib/api';

vi.mock('../../lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn() },
  TOKEN_KEY: 'booktracker_token',
}));

// Mirror production main.tsx defaults (lib/queryClient.ts): staleTime 5 min,
// retry 1. The 5-minute staleness window IS the bug mechanism — do not zero it.
function makeSharedClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 1000 * 60 * 5,
        retry: 1,
      },
    },
  });
}

function makeWrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('useUnreadCount — auth-boundary regression (UAT test 4)', () => {
  it('unread-count refetches after auth change instead of serving the previous user\'s cached count', async () => {
    const client = makeSharedClient();
    const wrapper = makeWrapper(client);

    // User X: no unread notifications — 0 lands in the cache, fresh for 5 min.
    vi.mocked(api.get).mockResolvedValue({ data: { count: 0 } } as never);
    const first = renderHook(() => useUnreadCount(), { wrapper });
    await waitFor(() => expect(first.result.current.data).toBe(0));
    first.unmount();

    // Auth boundary: sign-out (or defensive clear on sign-in) tears the cache down.
    await clearAuthSession(client);

    // User Y signs in with 1 unread notification waiting.
    vi.mocked(api.get).mockResolvedValue({ data: { count: 1 } } as never);
    const second = renderHook(() => useUnreadCount(), { wrapper });

    // The badge source refetches for user Y — not user X's fresh-cached 0.
    await waitFor(() => expect(second.result.current.data).toBe(1));
    expect(api.get).toHaveBeenCalledTimes(2);
  });

  it('control: without the auth-boundary teardown the second mount serves the stale cached 0 (the old bug)', async () => {
    const client = makeSharedClient();
    const wrapper = makeWrapper(client);

    // User X: 0 unread — cached fresh for 5 minutes.
    vi.mocked(api.get).mockResolvedValue({ data: { count: 0 } } as never);
    const first = renderHook(() => useUnreadCount(), { wrapper });
    await waitFor(() => expect(first.result.current.data).toBe(0));
    first.unmount();

    // NO clearAuthSession — simulates a sign-out path that forgot teardown.
    vi.mocked(api.get).mockResolvedValue({ data: { count: 1 } } as never);
    const second = renderHook(() => useUnreadCount(), { wrapper });

    // User X's fresh-cached 0 is served synchronously; no refetch ever fires
    // within the staleness window — user Y's badge is wrong (the reported bug).
    expect(second.result.current.data).toBe(0);
    await waitFor(() => expect(second.result.current.data).toBe(0));
    expect(api.get).toHaveBeenCalledTimes(1);
  });
});
