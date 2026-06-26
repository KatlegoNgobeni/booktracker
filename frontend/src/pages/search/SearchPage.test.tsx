/**
 * SearchPage.test.tsx — TDD RED phase tests for SearchPage (D-13/D-14)
 *
 * Behaviors tested:
 * 1. Renders search input and "Find your next book" empty state on initial load
 * 2. Submitting a query triggers GET /books/search with param `q` and renders result cards
 * 3. "Load more" is shown only when the last page length equals 10 (array-based pagination)
 */
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { SearchPage } from './SearchPage';
import { api } from '../../lib/api';

vi.mock('../../lib/api', () => ({
  api: { get: vi.fn() },
}));

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });
}

function renderSearchPage() {
  return render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter>
        <SearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('SearchPage', () => {
  it('renders the search input and "Find your next book" empty state on initial load', () => {
    renderSearchPage();
    // Search input must be present
    expect(screen.getByRole('searchbox')).toBeInTheDocument();
    // Empty state heading matches UI-SPEC copywriting contract
    expect(screen.getByText('Find your next book')).toBeInTheDocument();
  });

  it('submits query with param q and renders result cards from the returned array', async () => {
    const mockGet = vi.mocked(api.get);
    mockGet.mockResolvedValueOnce({
      data: [
        {
          olKey: '/works/OL1W',
          title: 'Dune',
          authors: ['Frank Herbert'],
          coverId: '12345',
          firstPublishYear: 1965,
        },
      ],
    } as never);

    renderSearchPage();

    const input = screen.getByRole('searchbox');
    fireEvent.change(input, { target: { value: 'dune' } });
    // Submit form to trigger search immediately (bypasses debounce in tests)
    fireEvent.submit(screen.getByRole('search'));

    await waitFor(() => {
      // CRITICAL assertion: param must be `q` not `query`
      expect(mockGet).toHaveBeenCalledWith(
        '/books/search',
        expect.objectContaining({
          params: expect.objectContaining({ q: 'dune' }),
        }),
      );
    });

    await waitFor(() => {
      expect(screen.getByText('Dune')).toBeInTheDocument();
      expect(screen.getByText('Frank Herbert')).toBeInTheDocument();
    });
  });

  it('shows Load more button when last fetched page has exactly 10 results', async () => {
    const mockGet = vi.mocked(api.get);
    const tenBooks = Array.from({ length: 10 }, (_, i) => ({
      olKey: `/works/OL${i}W`,
      title: `Book ${i}`,
      authors: null,
      coverId: null,
      firstPublishYear: null,
    }));
    mockGet.mockResolvedValueOnce({ data: tenBooks } as never);

    renderSearchPage();

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'fantasy' } });
    fireEvent.submit(screen.getByRole('search'));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /load more/i })).toBeInTheDocument();
    });
  });
});
