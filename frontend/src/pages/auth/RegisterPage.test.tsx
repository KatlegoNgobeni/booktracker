/**
 * RegisterPage.test.tsx — Smoke + auth-boundary tests for Register page (UI-01, 12-08)
 *
 * Test 3: RegisterPage renders email + password + display name inputs and "Create Account" button
 * Test 4 (12-08): successful registration clears any pre-existing query cache
 * before navigating — a fresh sign-in must never inherit another identity's
 * cached queries (defensive layer behind clearAuthSession; UAT tests 3/4).
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RegisterPage } from './RegisterPage';
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

function renderRegisterPage(client: QueryClient = makeClient()) {
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('RegisterPage', () => {
  it('Test 3: renders email, password, display name inputs and "Create Account" button', () => {
    renderRegisterPage();

    // Email input
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    // Password input
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    // Display name input
    expect(screen.getByLabelText(/display name/i)).toBeInTheDocument();
    // Submit button
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('Test 4: successful registration clears any pre-existing query cache before navigating', async () => {
    const user = userEvent.setup();
    const client = makeClient();

    // Simulate a stale session cache left behind by a missed sign-out teardown:
    // user A's identity is still in memory when a new account registers on this tab.
    client.setQueryData(QUERY_KEYS.me(), {
      id: 'user-1',
      email: 'reader@example.com',
      displayName: 'Avid Reader',
      createdAt: '2024-01-01T00:00:00Z',
    });
    vi.mocked(api.post).mockResolvedValue({ data: { token: 'jwt-b' } } as never);

    renderRegisterPage(client);

    await user.type(screen.getByLabelText(/display name/i), 'Bookworm B');
    await user.type(screen.getByLabelText(/email/i), 'user-b@example.com');
    await user.type(screen.getByLabelText(/password/i), 'hunter2hunter2');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    // The pre-existing cache is cleared — the new user never inherits user A's queries.
    await waitFor(() =>
      expect(client.getQueryData(QUERY_KEYS.me())).toBeUndefined(),
    );
    // New token stored and navigation to /shelf fired.
    expect(localStorage.getItem('booktracker_token')).toBe('jwt-b');
    expect(mockNavigate).toHaveBeenCalledWith('/shelf');
  });
});
