/**
 * AppHeader.test.tsx — Notification bell badge + mark-all-read interaction tests
 *
 * Tests (per PLAN.md acceptance criteria):
 * 1. Badge hidden when unread count is 0
 * 2. Badge shows the count when unread count > 0
 * 3. Clicking the bell calls the mark-all-read mutation
 * 4. Theme toggle button renders with aria-label "Toggle dark mode" (UI-02, 11-01)
 * 5. Toggle shows Sun icon when theme is dark, Moon icon when theme is light (11-01)
 * 6. Header identity shows the signed-in user's display name + avatar fallback (AVATAR-06, 12-05)
 *
 * Mocking strategy:
 * - useNotifications module mocked in full (hooks return controlled values)
 * - useWebSocket mocked as a no-op (STOMP/SockJS irrelevant for these tests)
 * - useTheme mocked so theme/toggle are controllable per test
 * - useCurrentUser mocked so header identity is controllable (12-05)
 * - QueryClientProvider wraps the render for useQueryClient() inside AppHeader
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AppHeader } from '../AppHeader';
import * as useNotificationsModule from '../../../hooks/useNotifications';
import * as useWebSocketModule from '../../../hooks/useWebSocket';
import * as useThemeModule from '../../../hooks/useTheme';
import * as useCurrentUserModule from '../../../hooks/useCurrentUser';

// Mock the entire notification hooks module
vi.mock('../../../hooks/useNotifications', () => ({
  useUnreadCount: vi.fn(),
  useMarkAllRead: vi.fn(),
  useNotifications: vi.fn(),
}));

// Mock the WebSocket hook to prevent STOMP/SockJS from connecting in tests
vi.mock('../../../hooks/useWebSocket', () => ({
  useWebSocket: vi.fn(),
}));

// Mock the theme hook so theme value and toggle are controllable (11-01)
vi.mock('../../../hooks/useTheme', () => ({
  useTheme: vi.fn(),
}));

// Mock the identity hook so header identity is controllable (AVATAR-06, 12-05)
vi.mock('../../../hooks/useCurrentUser', () => ({
  useCurrentUser: vi.fn(),
}));

const mockMarkAllRead = vi.fn();
const mockToggle = vi.fn();

/** Minimal QueryClient with retry disabled to avoid retry noise in tests */
function makeQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderHeader() {
  return render(
    <QueryClientProvider client={makeQueryClient()}>
      <AppHeader />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();

  // Default: WebSocket is a no-op
  vi.mocked(useWebSocketModule.useWebSocket).mockImplementation(() => {});

  // Default: light theme with controllable toggle
  vi.mocked(useThemeModule.useTheme).mockReturnValue({
    theme: 'light',
    toggle: mockToggle,
  });

  // Default: signed-in identity resolved (AVATAR-06)
  vi.mocked(useCurrentUserModule.useCurrentUser).mockReturnValue({
    data: {
      id: 'user-1',
      email: 'reader@example.com',
      displayName: 'Avid Reader',
      createdAt: '2024-01-01T00:00:00Z',
    },
  } as ReturnType<typeof useCurrentUserModule.useCurrentUser>);

  // Default: mark-all-read mutation (idle)
  vi.mocked(useNotificationsModule.useMarkAllRead).mockReturnValue({
    mutate: mockMarkAllRead,
    isPending: false,
  } as unknown as ReturnType<typeof useNotificationsModule.useMarkAllRead>);

  // Default: notification list empty (used by NotificationSheet)
  vi.mocked(useNotificationsModule.useNotifications).mockReturnValue({
    data: [],
    refetch: vi.fn(),
    isLoading: false,
    error: null,
    status: 'success',
  } as unknown as ReturnType<typeof useNotificationsModule.useNotifications>);
});

describe('AppHeader', () => {
  it('badge is hidden when unread count is 0', () => {
    vi.mocked(useNotificationsModule.useUnreadCount).mockReturnValue({
      data: 0,
    } as ReturnType<typeof useNotificationsModule.useUnreadCount>);

    renderHeader();

    // Badge is conditionally rendered only when count > 0.
    // Searching for '0' as badge text should return nothing.
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('badge shows the unread count when count is > 0', () => {
    vi.mocked(useNotificationsModule.useUnreadCount).mockReturnValue({
      data: 7,
    } as ReturnType<typeof useNotificationsModule.useUnreadCount>);

    renderHeader();

    expect(screen.getByText('7')).toBeInTheDocument();
  });

  it('clicking the bell opens the sheet and calls mark-all-read', () => {
    vi.mocked(useNotificationsModule.useUnreadCount).mockReturnValue({
      data: 3,
    } as ReturnType<typeof useNotificationsModule.useUnreadCount>);

    renderHeader();

    const bell = screen.getByRole('button', { name: /notifications/i });
    fireEvent.click(bell);

    // D-08: mark-all-read must be fired on bell click (open = mark all as read)
    expect(mockMarkAllRead).toHaveBeenCalledTimes(1);
  });

  it('badge shows 99+ when unread count exceeds 99', () => {
    vi.mocked(useNotificationsModule.useUnreadCount).mockReturnValue({
      data: 120,
    } as ReturnType<typeof useNotificationsModule.useUnreadCount>);

    renderHeader();

    expect(screen.getByText('99+')).toBeInTheDocument();
  });

  it('renders a dark-mode toggle button wired to useTheme.toggle (UI-02)', () => {
    vi.mocked(useNotificationsModule.useUnreadCount).mockReturnValue({
      data: 0,
    } as ReturnType<typeof useNotificationsModule.useUnreadCount>);

    renderHeader();

    const toggleBtn = screen.getByRole('button', { name: /toggle dark mode/i });
    expect(toggleBtn).toBeInTheDocument();

    fireEvent.click(toggleBtn);
    expect(mockToggle).toHaveBeenCalledTimes(1);
  });

  it('shows the Sun icon when theme is dark and the Moon icon when theme is light', () => {
    vi.mocked(useNotificationsModule.useUnreadCount).mockReturnValue({
      data: 0,
    } as ReturnType<typeof useNotificationsModule.useUnreadCount>);

    // Dark theme → Sun icon (clicking would switch back to light)
    vi.mocked(useThemeModule.useTheme).mockReturnValue({
      theme: 'dark',
      toggle: mockToggle,
    });
    const { unmount } = renderHeader();
    const darkToggle = screen.getByRole('button', { name: /toggle dark mode/i });
    expect(darkToggle.querySelector('svg.lucide-sun')).not.toBeNull();
    expect(darkToggle.querySelector('svg.lucide-moon')).toBeNull();
    unmount();

    // Light theme → Moon icon
    vi.mocked(useThemeModule.useTheme).mockReturnValue({
      theme: 'light',
      toggle: mockToggle,
    });
    renderHeader();
    const lightToggle = screen.getByRole('button', { name: /toggle dark mode/i });
    expect(lightToggle.querySelector('svg.lucide-moon')).not.toBeNull();
    expect(lightToggle.querySelector('svg.lucide-sun')).toBeNull();
  });

  it('shows the display name and avatar fallback in the header identity slot (AVATAR-06)', () => {
    vi.mocked(useNotificationsModule.useUnreadCount).mockReturnValue({
      data: 0,
    } as ReturnType<typeof useNotificationsModule.useUnreadCount>);

    renderHeader();

    // Display name text renders beside the avatar
    expect(screen.getByText('Avid Reader')).toBeInTheDocument();
    // jsdom never fires image load, so radix renders the initials fallback ("AR")
    expect(screen.getByText('AR')).toBeInTheDocument();
  });

  it('renders no identity text while useCurrentUser is pending (AVATAR-06 loading state)', () => {
    vi.mocked(useNotificationsModule.useUnreadCount).mockReturnValue({
      data: 0,
    } as ReturnType<typeof useNotificationsModule.useUnreadCount>);
    vi.mocked(useCurrentUserModule.useCurrentUser).mockReturnValue({
      data: undefined,
    } as ReturnType<typeof useCurrentUserModule.useCurrentUser>);

    renderHeader();

    expect(screen.queryByText('Avid Reader')).not.toBeInTheDocument();
    expect(screen.queryByText('AR')).not.toBeInTheDocument();
  });
});
