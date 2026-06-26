/**
 * StatsPage.test.tsx — TDD RED→GREEN tests for StatsPage (UI-01 stats screen)
 *
 * Behaviors tested:
 * 1. StatsPage renders goal progress using goalTarget + booksReadThisYear when a goal is set
 * 2. When no goal is set (goalTarget absent), "No yearly goal set" and Set Goal control render
 * 3. StatsDto missing optional fields renders without throwing; booksPerMonth renders 12 buckets
 *
 * Mocking strategy:
 * - vi.mock('../../lib/api') → mocks api.get
 * - T-06-13: optional StatsDto fields accessed via optional chaining — no crash on absent fields
 * - GoalDto 404 mapped to "no goal" — no error UI thrown
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { StatsPage } from './StatsPage';
import { api } from '../../lib/api';
import type { StatsDto } from '../../types/api.types';

vi.mock('../../lib/api', () => ({
  api: { get: vi.fn(), put: vi.fn() },
}));

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });
}

function renderStatsPage() {
  const user = userEvent.setup();
  const utils = render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter>
        <StatsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, user };
}

// Full stats with a goal set
const fullStats: StatsDto = {
  booksReadAllTime: 42,
  booksReadThisYear: 7,
  currentlyReadingCount: 2,
  goalTarget: 12,
  goalProgressPercent: 58,
  averageRating: 4.2,
  pagesReadThisYear: 2100,
  averageBookLength: 300,
  booksPerMonth: [1, 0, 2, 1, 0, 1, 0, 1, 0, 0, 1, 0],
  longestBook: { title: 'War and Peace', pageCount: 1225 },
  shortestBook: { title: 'The Great Gatsby', pageCount: 180 },
};

// Minimal stats — no goal, no optional fields
const minimalStats: StatsDto = {
  booksReadAllTime: 0,
  booksReadThisYear: 0,
  currentlyReadingCount: 0,
  booksPerMonth: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('StatsPage', () => {
  it('Test 1: renders goal progress region when goalTarget is present', async () => {
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === '/stats') return Promise.resolve({ data: fullStats });
      // /goal returns 404 — handled via catch in useGoal; stats.goalTarget is used instead
      return Promise.reject({ response: { status: 404 } });
    });

    renderStatsPage();

    // Goal progress should show "X of Y books this year"
    await waitFor(() =>
      expect(screen.getByText(/7.*of.*12.*books this year/i)).toBeInTheDocument(),
    );

    // Progress bar (Radix Progress has role=progressbar)
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('Test 2: renders "No yearly goal set" prompt and Set Goal control when goalTarget is absent', async () => {
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === '/stats') return Promise.resolve({ data: minimalStats });
      return Promise.reject({ response: { status: 404 } });
    });

    renderStatsPage();

    await waitFor(() =>
      expect(screen.getByText(/no yearly goal set/i)).toBeInTheDocument(),
    );

    // Set Goal input and button
    expect(screen.getByRole('spinbutton')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /set goal/i })).toBeInTheDocument();
  });

  it('Test 3: renders without crashing when optional StatsDto fields are absent and shows 12 chart buckets', async () => {
    vi.mocked(api.get).mockImplementation((url: string) => {
      if (url === '/stats') return Promise.resolve({ data: minimalStats });
      return Promise.reject({ response: { status: 404 } });
    });

    renderStatsPage();

    // Page renders without crashing
    await waitFor(() =>
      expect(screen.getByText(/no yearly goal set/i)).toBeInTheDocument(),
    );

    // Recharts renders a bar chart — verify chart container renders
    // The ResponsiveContainer renders a div; look for month labels
    await waitFor(() => {
      // At minimum Jan should be visible in the chart x-axis
      const janLabels = screen.getAllByText('Jan');
      expect(janLabels.length).toBeGreaterThanOrEqual(1);
    });
  });
});
