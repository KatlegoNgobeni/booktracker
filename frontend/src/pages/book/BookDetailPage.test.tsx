/**
 * BookDetailPage.test.tsx — TDD RED phase tests for BookDetailPage (D-15/D-16)
 *
 * Behaviors tested:
 * 1. Renders title, authors, and 3 add-to-shelf buttons when book is NOT on shelf
 * 2. When a matching shelf entry exists in cache, add buttons are replaced by badge + "View on Shelf"
 * 3. Clicking an add button calls POST /shelf with { olKey, status } and invalidates shelf query
 */
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { BookDetailPage } from './BookDetailPage';
import { api } from '../../lib/api';
import type { ShelfEntry, Page } from '../../types/api.types';

vi.mock('../../lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const MOCK_OL_KEY = '/works/OL1W';
const ENCODED_OL_KEY = encodeURIComponent(MOCK_OL_KEY);

const MOCK_BOOK_DETAIL = {
  olKey: MOCK_OL_KEY,
  title: 'Dune',
  authors: 'Frank Herbert',
  coverId: '12345',
  pageCount: 412,
  firstPublishYear: 1965,
  description: 'A epic science fiction novel set in the far future.',
};

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });
}

function renderBookDetailPage(client: QueryClient = makeClient()) {
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/books/${ENCODED_OL_KEY}`]}>
        <Routes>
          <Route path="/books/:olKey" element={<BookDetailPage />} />
          <Route path="/shelf" element={<div>Shelf</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('BookDetailPage', () => {
  it('renders title, authors, and 3 add-to-shelf buttons when book is not on shelf', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: MOCK_BOOK_DETAIL } as never);

    renderBookDetailPage();

    // Wait for the book data to load
    await waitFor(() => {
      expect(screen.getByText('Dune')).toBeInTheDocument();
    });

    expect(screen.getByText('Frank Herbert')).toBeInTheDocument();

    // All 3 add-to-shelf buttons must be present
    expect(screen.getByRole('button', { name: /want to read/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /currently reading/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^read$/i })).toBeInTheDocument();
  });

  it('shows "On shelf" badge and "View on Shelf" link when book is already on shelf (D-16)', async () => {
    // Pre-populate shelf cache so useShelfEntryForBook can detect the book
    const client = makeClient();
    const shelfEntry: ShelfEntry = {
      entryId: 'entry-abc',
      status: 'WANT_TO_READ',
      olKey: MOCK_OL_KEY,
      title: 'Dune',
      authors: 'Frank Herbert',
      coverId: '12345',
      rating: null,
      review: null,
      currentPage: null,
      dateStarted: null,
      dateFinished: null,
      createdAt: '2024-01-15T10:00:00+02:00',
    };
    const shelfPage: Page<ShelfEntry> = {
      content: [shelfEntry],
      number: 0,
      size: 10,
      totalPages: 1,
      totalElements: 1,
    };
    client.setQueryData(['shelf', 'WANT_TO_READ'], shelfPage);

    vi.mocked(api.get).mockResolvedValueOnce({ data: MOCK_BOOK_DETAIL } as never);

    renderBookDetailPage(client);

    await waitFor(() => {
      expect(screen.getByText('Dune')).toBeInTheDocument();
    });

    // Add buttons must NOT be present
    expect(screen.queryByRole('button', { name: /want to read/i })).not.toBeInTheDocument();

    // Badge and "View on Shelf" link must be present
    expect(screen.getByText(/on shelf/i)).toBeInTheDocument();
    expect(screen.getByText(/want to read/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /view on shelf/i })).toBeInTheDocument();
  });

  it('clicking an add button calls POST /shelf with { olKey, status } and invalidates shelf', async () => {
    const client = makeClient();
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries');

    vi.mocked(api.get).mockResolvedValueOnce({ data: MOCK_BOOK_DETAIL } as never);
    vi.mocked(api.post).mockResolvedValueOnce({
      data: {
        entryId: 'new-entry',
        status: 'WANT_TO_READ',
        olKey: MOCK_OL_KEY,
        title: 'Dune',
        authors: 'Frank Herbert',
        coverId: '12345',
        rating: null,
        review: null,
        currentPage: null,
        dateStarted: null,
        dateFinished: null,
        createdAt: '2024-01-15T10:00:00+02:00',
      },
    } as never);

    renderBookDetailPage(client);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /want to read/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /want to read/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/shelf', {
        olKey: MOCK_OL_KEY,
        status: 'WANT_TO_READ',
      });
    });

    // Verify shelf invalidation was called with object form (TanStack Query v5)
    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith(
        expect.objectContaining({ queryKey: expect.arrayContaining(['shelf']) }),
      );
    });
  });
});
