/**
 * ProfilePage.test.tsx — TDD RED→GREEN tests for ProfilePage (D-03/D-08)
 *
 * Behaviors tested:
 * 1. ProfilePage renders displayName and email from GET /users/me
 * 2. Toggling the dark mode switch adds/removes the `dark` class on document.documentElement
 *    and writes booktracker_theme to localStorage
 * 3. Sign Out removes booktracker_token and navigates to /login
 *
 * Mocking strategy:
 * - vi.mock('../../lib/api') → mocks api.get
 * - jsdom localStorage is used directly
 * - useNavigate mocked via MemoryRouter
 *
 * D-08: no profile editing — only identity display, theme toggle, sign out
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

function renderProfilePage() {
  const user = userEvent.setup();
  const utils = render(
    <QueryClientProvider client={makeClient()}>
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

  it('Test 2: toggling dark mode switch adds/removes dark class and persists to localStorage', async () => {
    const { user } = renderProfilePage();

    // Wait for profile to load
    await waitFor(() =>
      expect(screen.getByText('Avid Reader')).toBeInTheDocument(),
    );

    // Find the dark mode switch
    const darkSwitch = screen.getByRole('switch', { name: /dark mode/i });

    // Initially light (localStorage empty → default is 'light')
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem('booktracker_theme')).not.toBe('dark');

    // Toggle ON → dark
    await user.click(darkSwitch);

    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('booktracker_theme')).toBe('dark');

    // Toggle OFF → light
    await user.click(darkSwitch);

    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(localStorage.getItem('booktracker_theme')).toBe('light');
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
});
