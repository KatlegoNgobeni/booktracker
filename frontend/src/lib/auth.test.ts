/**
 * auth.test.ts — clearAuthSession behavior tests (AVATAR-06 gap closure, 12-07)
 *
 * Verifies the centralized auth-boundary teardown helper:
 * - Token removal from localStorage
 * - Full QueryClient cache clear (the UAT 3/4 root cause)
 * - SW 'api-cache' Cache Storage purge (awaited, rejection-swallowing)
 * - Synchronous token + memory teardown for fire-and-forget callers
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import { clearAuthSession, TOKEN_KEY } from './auth';

describe('clearAuthSession (auth.ts)', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('Test 1: removes booktracker_token from localStorage', async () => {
    localStorage.setItem(TOKEN_KEY, 'some-jwt');
    expect(localStorage.getItem(TOKEN_KEY)).toBe('some-jwt');

    await clearAuthSession(new QueryClient());

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('Test 2: clears all cached queries on the QueryClient it receives', async () => {
    const client = new QueryClient();
    client.setQueryData(['me'], { id: 'user-a', displayName: 'User A' });
    client.setQueryData(['notifications', 'unread-count'], 0);

    await clearAuthSession(client);

    expect(client.getQueryData(['me'])).toBeUndefined();
    expect(client.getQueryData(['notifications', 'unread-count'])).toBeUndefined();
  });

  it("Test 3: awaits caches.delete('api-cache') when a CacheStorage API is present", async () => {
    const deleteMock = vi.fn().mockResolvedValue(true);
    vi.stubGlobal('caches', { delete: deleteMock });

    await clearAuthSession(new QueryClient());

    expect(deleteMock).toHaveBeenCalledWith('api-cache');
  });

  it('Test 4: resolves without throwing when no CacheStorage API exists', async () => {
    // Default jsdom has no `caches` global — must not throw.
    await expect(clearAuthSession(new QueryClient())).resolves.toBeUndefined();
  });

  it('Test 5: token removal and QueryClient.clear() are synchronous (fire-and-forget safe)', () => {
    localStorage.setItem(TOKEN_KEY, 'sync-jwt');
    const client = new QueryClient();
    client.setQueryData(['me'], { id: 'user-a' });

    // Deliberately NOT awaited — SPA sign-out callers fire-and-forget this
    // helper, so the two synchronous teardown steps must land before the
    // next microtask.
    void clearAuthSession(client);

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(client.getQueryData(['me'])).toBeUndefined();
  });
});
