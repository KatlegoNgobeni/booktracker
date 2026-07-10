/**
 * LoginPage.test.tsx — Smoke + auth-boundary tests for Login page (UI-01, 12-08)
 *
 * Test 2: LoginPage renders email + password inputs (with <Label htmlFor>) and a "Sign In" button
 * Test 4 (12-08): successful login clears any pre-existing query cache before
 * navigating — a fresh sign-in must never inherit another identity's cached
 * queries (defensive layer behind clearAuthSession; UAT tests 3/4).
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LoginPage } from './LoginPage';
import { api } from '../../lib/api';
import { QUERY_KEYS } from '../../lib/queryKeys';

// Mock the api module so no actual HTTP calls happen
vi.mock('../../lib/api', () => ({
  TOKEN_KEY: 'booktracker_token',
  api: {
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
}));

// Capture navigate calls (same importOriginal pattern as ProfilePage.test.tsx)
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function renderLoginPage(client: QueryClient = makeClient()) {
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('LoginPage', () => {
  it('Test 2: renders email + password inputs and "Sign In" button', () => {
    renderLoginPage();

    // Email input with label
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    // Password input with label
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    // Submit button
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('Test 4: successful login clears any pre-existing query cache before navigating', async () => {
    const user = userEvent.setup();
    const client = makeClient();

    // Simulate a stale session cache left behind by a missed sign-out teardown:
    // user A's identity is still in memory when user B signs in on this tab.
    client.setQueryData(QUERY_KEYS.me(), {
      id: 'user-1',
      email: 'reader@example.com',
      displayName: 'Avid Reader',
      createdAt: '2024-01-01T00:00:00Z',
    });
    vi.mocked(api.post).mockResolvedValue({ data: { token: 'jwt-b' } } as never);

    renderLoginPage(client);

    await user.type(screen.getByLabelText(/email/i), 'user-b@example.com');
    await user.type(screen.getByLabelText(/password/i), 'hunter2hunter2');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    // The pre-existing cache is cleared — user B never inherits user A's queries.
    await waitFor(() =>
      expect(client.getQueryData(QUERY_KEYS.me())).toBeUndefined(),
    );
    // New token stored and navigation to /shelf fired.
    expect(localStorage.getItem('booktracker_token')).toBe('jwt-b');
    expect(mockNavigate).toHaveBeenCalledWith('/shelf');
  });
});
