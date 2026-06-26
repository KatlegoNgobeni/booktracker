/**
 * ShelfPage.test.tsx — TDD RED→GREEN tests for ShelfPage (D-09/D-10)
 *
 * Behaviors tested:
 * 1. ShelfPage renders three tab controls labelled Want to Read / Currently Reading / Read
 * 2a. A Currently Reading entry with non-null pageCount renders a progress bar
 * 2b. A Currently Reading entry with null pageCount renders "Page {n}" text and no progress bar
 * 3. A Read entry renders its StarRating (read-only) — role="group" aria-label="Star rating"
 *
 * Note on Radix UI Tabs: TabsContent is lazy-mounted — only the active tab's content renders
 * initially. Clicking a tab mounts its content, triggering the useShelfList query.
 * Use userEvent (not fireEvent) so that React async state updates process in act().
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { ShelfPage } from './ShelfPage';
import { api } from '../../lib/api';
import type { ShelfEntry, Page } from '../../types/api.types';

vi.mock('../../lib/api', () => ({
  api: { get: vi.fn() },
}));

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });
}

function renderShelfPage() {
  const user = userEvent.setup();
  const utils = render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter>
        <ShelfPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, user };
}

const emptyPage: Page<ShelfEntry> = {
  content: [],
  number: 0,
  size: 20,
  totalPages: 0,
  totalElements: 0,
};

const currentlyReadingWithPageCount: ShelfEntry = {
  entryId: 'entry-1',
  status: 'CURRENTLY_READING',
  title: 'The Hobbit',
  olKey: '/works/OL1W',
  coverId: null,
  authors: 'J.R.R. Tolkien',
  rating: null,
  review: null,
  currentPage: 100,
  pageCount: 310,
  dateStarted: '2024-01-01',
  dateFinished: null,
  createdAt: '2024-01-01T00:00:00Z',
};

const currentlyReadingNullPageCount: ShelfEntry = {
  ...currentlyReadingWithPageCount,
  entryId: 'entry-2',
  currentPage: 50,
  pageCount: null,
};

const readEntryWithRating: ShelfEntry = {
  entryId: 'entry-3',
  status: 'READ',
  title: 'Dune',
  olKey: '/works/OL2W',
  coverId: null,
  authors: 'Frank Herbert',
  rating: 4,
  review: 'Epic sci-fi',
  currentPage: null,
  pageCount: null,
  dateStarted: null,
  dateFinished: '2024-01-10',
  createdAt: '2024-01-01T00:00:00Z',
};

beforeEach(() => {
  vi.clearAllMocks();
  // Default: all status queries return empty pages
  vi.mocked(api.get).mockResolvedValue({ data: emptyPage } as never);
});

describe('ShelfPage', () => {
  it('Test 1: renders three tab controls labelled Want to Read, Currently Reading, Read', async () => {
    // Wait for initial query to resolve before asserting tabs
    const { user } = renderShelfPage();
    void user; // used in other tests

    // Three tabs must be present — use exact text to avoid "Currently Reading" matching /read/i
    expect(screen.getByRole('tab', { name: 'Want to Read' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Currently Reading' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Read' })).toBeInTheDocument();
  });

  it('Test 2a: a Currently Reading entry with non-null pageCount renders a progress bar', async () => {
    vi.mocked(api.get).mockImplementation((_url: string, cfg: any) => {
      if (cfg?.params?.status === 'CURRENTLY_READING') {
        return Promise.resolve({
          data: {
            content: [currentlyReadingWithPageCount],
            number: 0,
            size: 20,
            totalPages: 1,
            totalElements: 1,
          },
        });
      }
      return Promise.resolve({ data: emptyPage });
    });

    const { user } = renderShelfPage();

    // Click "Currently Reading" tab — userEvent wraps in act() for proper async flush
    await user.click(screen.getByRole('tab', { name: 'Currently Reading' }));

    // Wait for the query to resolve and the entry to render
    await waitFor(() =>
      expect(screen.getByText('The Hobbit')).toBeInTheDocument(),
    );

    // Progress bar must be present (Radix Progress root has role="progressbar")
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('Test 2b: a Currently Reading entry with null pageCount renders page text and no progress bar', async () => {
    vi.mocked(api.get).mockImplementation((_url: string, cfg: any) => {
      if (cfg?.params?.status === 'CURRENTLY_READING') {
        return Promise.resolve({
          data: {
            content: [currentlyReadingNullPageCount],
            number: 0,
            size: 20,
            totalPages: 1,
            totalElements: 1,
          },
        });
      }
      return Promise.resolve({ data: emptyPage });
    });

    const { user } = renderShelfPage();

    await user.click(screen.getByRole('tab', { name: 'Currently Reading' }));

    await waitFor(() =>
      expect(screen.getByText('The Hobbit')).toBeInTheDocument(),
    );

    // Text-only: "Page 50"
    expect(screen.getByText('Page 50')).toBeInTheDocument();
    // No progress bar when pageCount is null
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('Test 3: a Read entry renders a read-only StarRating group', async () => {
    vi.mocked(api.get).mockImplementation((_url: string, cfg: any) => {
      if (cfg?.params?.status === 'READ') {
        return Promise.resolve({
          data: {
            content: [readEntryWithRating],
            number: 0,
            size: 20,
            totalPages: 1,
            totalElements: 1,
          },
        });
      }
      return Promise.resolve({ data: emptyPage });
    });

    const { user } = renderShelfPage();

    // Click "Read" tab — exact name to avoid ambiguity with "Currently Reading"
    await user.click(screen.getByRole('tab', { name: 'Read' }));

    await waitFor(() =>
      expect(screen.getByText('Dune')).toBeInTheDocument(),
    );

    // StarRating read-only mode: role="group" with aria-label="Star rating"
    // Interactive mode buttons labelled "Rate N stars" must NOT appear
    expect(screen.queryByRole('button', { name: /rate/i })).not.toBeInTheDocument();
    expect(screen.getByRole('group', { name: /star rating/i })).toBeInTheDocument();
  });
});
