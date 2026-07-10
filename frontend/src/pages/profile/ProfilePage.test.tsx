/**
 * ProfilePage.test.tsx — TDD RED→GREEN tests for ProfilePage (D-03/D-08)
 *
 * Behaviors tested:
 * 1. ProfilePage renders displayName and email from GET /users/me
 * 2. ProfilePage no longer renders a dark mode switch (Phase 11 D-05 — theme is
 *    controlled globally from the AppHeader toggle; see AppHeader.test.tsx / useTheme.test.ts)
 * 3. Sign Out removes booktracker_token and navigates to /login
 * 4. Sign Out clears the TanStack Query cache (auth-boundary teardown, 12-08)
 * 5. User switch regression (UAT test 3): same-tab sign-out → sign-in as a
 *    different user refetches /users/me instead of serving the old identity
 *
 * Note: clearAuthSession from the REAL lib/auth.ts runs inside these tests —
 * intentional, so the assertions exercise the true production teardown.
 *
 * Mocking strategy:
 * - vi.mock('../../lib/api') → mocks api.get
 * - jsdom localStorage is used directly
 * - useNavigate mocked via MemoryRouter
 *
 * D-08: no profile editing — only identity display and sign out
 * TOKEN_KEY = 'booktracker_token'
 * THEME_KEY = 'booktracker_theme'
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { ProfilePage } from './ProfilePage';
import { api } from '../../lib/api';
import { QUERY_KEYS } from '../../lib/queryKeys';

vi.mock('../../lib/api', () => ({
  api: { get: vi.fn() },
  TOKEN_KEY: 'booktracker_token',
}));

// Capture navigate calls
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const meResponse = {
  id: 'user-1',
  email: 'reader@example.com',
  displayName: 'Avid Reader',
  createdAt: '2024-01-01T00:00:00Z',
};

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });
}

// Accepts an external QueryClient so tests can seed the cache before render
// and share ONE client across two mounts (user switch regression below).
function renderProfilePage(client: QueryClient = makeClient()) {
  const user = userEvent.setup();
  const utils = render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, user };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Reset dark class
  document.documentElement.classList.remove('dark', 'light');
  vi.mocked(api.get).mockResolvedValue({ data: meResponse } as never);
});

describe('ProfilePage', () => {
  it('Test 1: renders displayName and email from GET /users/me', async () => {
    renderProfilePage();

    await waitFor(() =>
      expect(screen.getByText('Avid Reader')).toBeInTheDocument(),
    );
    expect(screen.getByText('reader@example.com')).toBeInTheDocument();
  });

  it('Test 2: does not render a dark mode switch (theme is controlled from AppHeader, D-05)', async () => {
    renderProfilePage();

    // Wait for profile to load
    await waitFor(() =>
      expect(screen.getByText('Avid Reader')).toBeInTheDocument(),
    );

    // The Preferences section and its switch were removed in Phase 11 Plan 02
    expect(screen.queryByRole('switch', { name: /dark mode/i })).toBeNull();
    expect(screen.queryByText(/preferences/i)).toBeNull();
  });

  it('Test 3: Sign Out clears booktracker_token and navigates to /login', async () => {
    localStorage.setItem('booktracker_token', 'test-jwt-token');
    const { user } = renderProfilePage();

    await waitFor(() =>
      expect(screen.getByText('Avid Reader')).toBeInTheDocument(),
    );

    const signOutButton = screen.getByRole('button', { name: /sign out/i });
    await user.click(signOutButton);

    // Token must be removed
    expect(localStorage.getItem('booktracker_token')).toBeNull();

    // Navigation to /login with replace
    expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
  });

  it('Test 4: Sign Out clears the TanStack Query cache', async () => {
    localStorage.setItem('booktracker_token', 'test-jwt-token');

    // staleTime Infinity mirrors production ['me'] semantics — the seeded
    // entries render without a mount refetch. api.get never resolves so a
    // post-clear refetch cannot repopulate the cache before the assertions.
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity } },
    });
    client.setQueryData(QUERY_KEYS.me(), meResponse);
    client.setQueryData(['notifications', 'unread-count'], 0);
    vi.mocked(api.get).mockImplementation(() => new Promise(() => {}));

    const { user } = renderProfilePage(client);

    await waitFor(() =>
      expect(screen.getByText('Avid Reader')).toBeInTheDocument(),
    );

    await user.click(screen.getByRole('button', { name: /sign out/i }));

    // The entire query cache must be torn down at the auth boundary —
    // the next user in this tab must not inherit identity or unread-count.
    expect(client.getQueryData(QUERY_KEYS.me())).toBeUndefined();
    expect(client.getQueryData(['notifications', 'unread-count'])).toBeUndefined();

    // Token removed and navigation preserved (existing behavior).
    expect(localStorage.getItem('booktracker_token')).toBeNull();
    expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
  });
});

describe('ProfilePage — user switch regression (UAT test 3)', () => {
  const userB = {
    id: 'user-2',
    email: 'switcher@example.com',
    displayName: 'Bookworm B',
    createdAt: '2025-06-01T00:00:00Z',
  };

  it('refetches /users/me after sign-out → sign-in as another user in the same tab', async () => {
    // ONE shared QueryClient across both mounts, staleTime Infinity for
    // queries — mirrors production ['me'] semantics. Without the auth-boundary
    // teardown, user A's identity would be served from cache forever.
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity } },
    });

    let currentUser = meResponse;
    vi.mocked(api.get).mockImplementation(((url: string) =>
      url === '/users/me'
        ? Promise.resolve({ data: currentUser })
        : Promise.resolve({
            data: { followerCount: 0, followingCount: 0, entries: [] },
          })) as never);

    // Mount 1: user A signs in and views their profile.
    const first = renderProfilePage(client);
    await waitFor(() =>
      expect(screen.getByText('Avid Reader')).toBeInTheDocument(),
    );

    // Re-point the identity mock to user B BEFORE clicking Sign Out: navigate
    // is mocked, so unlike production the page stays mounted for an instant
    // after the cache clear and its observer refires /users/me immediately.
    // Pointing at user B first keeps that in-flight refetch from re-caching
    // user A (in production, real navigation unmounts ProfilePage first).
    currentUser = userB;

    await first.user.click(screen.getByRole('button', { name: /sign out/i }));
    first.unmount();

    // Mount 2: same tab, same QueryClient, no reload — user B signs in.
    renderProfilePage(client);

    // The new identity renders immediately — not user A's cached profile.
    await waitFor(() =>
      expect(screen.getByText('Bookworm B')).toBeInTheDocument(),
    );
    expect(screen.queryByText('Avid Reader')).toBeNull();

    // /users/me was fetched a second time — refetched, not served from cache.
    const meCalls = vi
      .mocked(api.get)
      .mock.calls.filter(([url]) => url === '/users/me');
    expect(meCalls.length).toBeGreaterThanOrEqual(2);
  });
});
