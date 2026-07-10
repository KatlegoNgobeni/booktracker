/**
 * api.test.ts — Axios interceptor behavior tests (UI-02)
 *
 * Tests 1-4 verify the JWT interceptor patterns in api.ts:
 * - Token attachment on requests
 * - No Authorization header when unauthenticated
 * - 401 redirect and token removal (non-auth endpoints)
 * - No redirect loop for /auth/login 401
 *
 * Tests 5-7 (12-07) verify centralized 401 session teardown:
 * - Singleton queryClient cleared on non-auth 401
 * - SW 'api-cache' purged on non-auth 401
 * - /auth/* 401 leaves the query cache intact (guard regression)
 *
 * Uses vi.mock — msw excluded per RESEARCH package legitimacy gate (SLOP verdict).
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { TOKEN_KEY } from './api';
import { queryClient } from './queryClient';

// Helper: get the registered interceptors by rebuilding them inline
// We test the interceptor logic directly via the exported api instance

describe('Axios interceptor: request (api.ts)', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('Test 1: attaches Authorization: Bearer <token> when token is in localStorage', async () => {
    localStorage.setItem(TOKEN_KEY, 'test-jwt-token');

    // Import api fresh (it reads localStorage in the interceptor)
    const { api } = await import('./api');

    // Intercept the request before it goes out by checking the interceptors
    // We invoke the request interceptor handler directly
    const requestInterceptors = (api.interceptors.request as any).handlers;
    expect(requestInterceptors.length).toBeGreaterThan(0);

    const config = { headers: { Authorization: '' } };
    const result = await requestInterceptors[0].fulfilled(config);
    expect(result.headers.Authorization).toBe('Bearer test-jwt-token');
  });

  it('Test 2: sends no Authorization header when no token present', async () => {
    localStorage.removeItem(TOKEN_KEY);

    const { api } = await import('./api');

    const requestInterceptors = (api.interceptors.request as any).handlers;
    const config = { headers: {} as Record<string, string> };
    const result = await requestInterceptors[0].fulfilled(config);
    expect(result.headers.Authorization).toBeUndefined();
  });
});

describe('Axios interceptor: response 401 handling (api.ts)', () => {
  let originalLocation: Location;

  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    // Reset the shared singleton so tests stay independent (12-07)
    queryClient.clear();
    // Store original location
    originalLocation = window.location;
    // Allow location.href to be set in tests
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { href: '/' },
    });
  });

  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
    // Restore original location
    Object.defineProperty(window, 'location', {
      writable: true,
      value: originalLocation,
    });
  });

  it('Test 3: 401 on non-/auth URL removes token and redirects to /login', async () => {
    localStorage.setItem(TOKEN_KEY, 'my-token');

    const { api } = await import('./api');
    const responseInterceptors = (api.interceptors.response as any).handlers;
    expect(responseInterceptors.length).toBeGreaterThan(0);

    const err = {
      response: { status: 401 },
      config: { url: '/shelf' },
    };

    // The response error interceptor should remove token and redirect
    await responseInterceptors[0].rejected(err).catch(() => {
      // Expected to reject (re-throw)
    });

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(window.location.href).toBe('/login');
  });

  it('Test 4: 401 on /auth/login URL does NOT clear token or redirect (no loop)', async () => {
    localStorage.setItem(TOKEN_KEY, 'my-token');

    const { api } = await import('./api');
    const responseInterceptors = (api.interceptors.response as any).handlers;

    const err = {
      response: { status: 401 },
      config: { url: '/auth/login' },
    };

    window.location.href = '/';

    await responseInterceptors[0].rejected(err).catch(() => {
      // Expected to reject (re-throw)
    });

    // Token should NOT be removed
    expect(localStorage.getItem(TOKEN_KEY)).toBe('my-token');
    // Location should NOT change to /login
    expect(window.location.href).toBe('/');
  });

  it('Test 5: 401 on non-/auth URL clears the singleton queryClient and redirects', async () => {
    localStorage.setItem(TOKEN_KEY, 'my-token');
    queryClient.setQueryData(['me'], { id: 'user-a' });

    const { api } = await import('./api');
    const responseInterceptors = (api.interceptors.response as any).handlers;

    const err = {
      response: { status: 401 },
      config: { url: '/shelf' },
    };

    await responseInterceptors[0].rejected(err).catch(() => {
      // Expected to reject (re-throw)
    });

    expect(queryClient.getQueryData(['me'])).toBeUndefined();
    expect(window.location.href).toBe('/login');
  });

  it("Test 6: 401 on non-/auth URL purges the SW api-cache via caches.delete('api-cache')", async () => {
    localStorage.setItem(TOKEN_KEY, 'my-token');
    const deleteMock = vi.fn().mockResolvedValue(true);
    vi.stubGlobal('caches', { delete: deleteMock });

    const { api } = await import('./api');
    const responseInterceptors = (api.interceptors.response as any).handlers;

    const err = {
      response: { status: 401 },
      config: { url: '/shelf' },
    };

    await responseInterceptors[0].rejected(err).catch(() => {
      // Expected to reject (re-throw)
    });

    expect(deleteMock).toHaveBeenCalledWith('api-cache');
  });

  it('Test 7: 401 on /auth/login leaves the seeded queryClient cache intact (guard regression)', async () => {
    localStorage.setItem(TOKEN_KEY, 'my-token');
    queryClient.setQueryData(['me'], { id: 'user-a' });

    const { api } = await import('./api');
    const responseInterceptors = (api.interceptors.response as any).handlers;

    const err = {
      response: { status: 401 },
      config: { url: '/auth/login' },
    };

    await responseInterceptors[0].rejected(err).catch(() => {
      // Expected to reject (re-throw)
    });

    // Cache entry must survive — extends Test 4's no-loop guarantee to the cache
    expect(queryClient.getQueryData(['me'])).toEqual({ id: 'user-a' });
    expect(localStorage.getItem(TOKEN_KEY)).toBe('my-token');
  });
});
