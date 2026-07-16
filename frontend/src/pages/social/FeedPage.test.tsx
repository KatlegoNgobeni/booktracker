/**
 * FeedPage.test.tsx — Wave 1 RED tests for DISC-01/02/04 discovery behaviors
 *
 * These tests are intentionally RED until Plans 02-04 add:
 *   - DISC-01: "Friends are reading" section (FriendsReadingSection)
 *   - DISC-02: Book cards linking to /books/:olKey in the discovery section
 *   - DISC-04: "Friends finished recently" section heading
 *
 * Mocking strategy: vi.mock('../../lib/api') — same pattern as ShelfPage/StatsPage tests.
 * MSW is not installed in this project; api.get is mocked directly.
 *
 * All three tests will FAIL at runtime until FeedPage renders the new discovery sections.
 * Zero TypeScript compile errors — FeedPage, api, MemoryRouter, and QueryClient are all present.
 */
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { FeedPage } from './FeedPage';
import { api } from '../../lib/api';
import type { Page, FeedItem } from '../../types/api.types';

// Mock the api module — same pattern used by ShelfPage.test.tsx and StatsPage.test.tsx
vi.mock('../../lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
  TOKEN_KEY: 'booktracker_token',
}));

// Also mock useSocial for PendingRequestsWidget (used inside FeedPage)
vi.mock('../../hooks/useSocial', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../hooks/useSocial')>();
  return {
    ...original,
    usePendingReceivedRequests: vi.fn().mockReturnValue({ data: [] }),
    useFeed: vi.fn().mockReturnValue({
      data: undefined,
      isPending: false,
      isError: false,
      fetchNextPage: vi.fn(),
      hasNextPage: false,
      isFetchingNextPage: false,
      refetch: vi.fn(),
    }),
    useFriendsReading: vi.fn().mockReturnValue({
      data: {
        content: [
          {
            entryId: 'fr-1',
            userId: 'u-2',
            displayName: 'Alice',
            bookTitle: 'Dune',
            bookOlKey: 'OL123W',
            bookCoverId: null,
            bookAuthors: 'Frank Herbert',
          },
        ],
        number: 0,
        size: 10,
        totalPages: 1,
        totalElements: 1,
      },
      isPending: false,
      isError: false,
    }),
  };
});

const emptyFeedPage: Page<FeedItem> = {
  content: [],
  number: 0,
  size: 20,
  totalPages: 0,
  totalElements: 0,
};

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });
}

function renderFeedPage() {
  return render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter>
        <FeedPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();

  // Default: activity feed returns empty page
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url.includes('/feed/friends-reading')) {
      return Promise.resolve({
        data: {
          content: [
            {
              entryId: 'fr-1',
              userId: 'u-2',
              displayName: 'Alice',
              bookTitle: 'Dune',
              bookOlKey: 'OL123W',
              bookCoverId: null,
              bookAuthors: 'Frank Herbert',
            },
          ],
          number: 0,
          size: 10,
          totalPages: 1,
          totalElements: 1,
        },
      } as never);
    }
    // /api/feed and others
    return Promise.resolve({ data: emptyFeedPage } as never);
  });
});

describe('FeedPage — DISC-01/02/04 discovery (Wave 1 RED)', () => {
  /**
   * DISC-01: "Friends are reading" discovery section must be present.
   * This test will FAIL until FeedPage adds the FriendsReadingSection component.
   */
  it('renders Friends are reading section heading (DISC-01)', async () => {
    renderFeedPage();

    // This heading does not exist yet — test is RED until Plan 02 adds the section
    await waitFor(() => {
      expect(screen.getByText('Friends are reading')).toBeInTheDocument();
    });
  });

  /**
   * DISC-02: Book cards in the discovery section must link to /books/:olKey.
   * This test will FAIL until FeedPage renders FriendsReadingSection with book card links.
   */
  it('book card in discovery section links to /books/:olKey (DISC-02)', async () => {
    renderFeedPage();

    // Wait for "Dune" to appear in the friends-reading section
    const duneText = await screen.findByText('Dune');
    expect(duneText).toBeInTheDocument();

    // A link to /books/OL123W must be present in the document
    const links = document.querySelectorAll('a[href*="/books/OL123W"]');
    expect(links.length).toBeGreaterThan(0);
  });

  /**
   * DISC-04: "Friends finished recently" section heading must be present below discovery.
   * This test will FAIL until FeedPage or a section component renders this heading.
   */
  it('Friends finished recently section is present below discovery (DISC-04)', async () => {
    renderFeedPage();

    await waitFor(() => {
      expect(
        screen.getByText(/Friends finished recently/i),
      ).toBeInTheDocument();
    });
  });
});
