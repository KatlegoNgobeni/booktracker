/**
 * ShelfPage.test.tsx — TDD RED phase tests for ShelfPage (D-09/D-10)
 *
 * Behaviors tested:
 * 1. ShelfPage renders three tab controls labelled Want to Read / Currently Reading / Read
 * 2a. A Currently Reading entry with non-null pageCount renders a progress bar
 * 2b. A Currently Reading entry with null pageCount renders "Page {n}" text and no progress bar
 * 3. A Read entry renders its StarRating (read-only) — role="group" aria-label="Star rating"
 */
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
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
  return render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter>
        <ShelfPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
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
  title: 'The Hobbit',
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
  // Default: all statuses return empty pages
  vi.mocked(api.get).mockResolvedValue({ data: emptyPage } as never);
});

describe('ShelfPage', () => {
  it('Test 1: renders three tab controls labelled Want to Read, Currently Reading, Read', () => {
    renderShelfPage();

    expect(screen.getByRole('tab', { name: /want to read/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /currently reading/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /read/i })).toBeInTheDocument();
  });

  it('Test 2a: a Currently Reading entry with non-null pageCount renders a progress bar', async () => {
    vi.mocked(api.get).mockImplementation((_url: string, config: any) => {
      const status = config?.params?.status;
      if (status === 'CURRENTLY_READING') {
        return Promise.resolve({
          data: {
            ...emptyPage,
            content: [currentlyReadingWithPageCount],
            totalElements: 1,
            totalPages: 1,
          },
        });
      }
      return Promise.resolve({ data: emptyPage });
    });

    renderShelfPage();

    // Switch to Currently Reading tab
    fireEvent.click(screen.getByRole('tab', { name: /currently reading/i }));

    await waitFor(() => {
      expect(screen.getByText('The Hobbit')).toBeInTheDocument();
    });

    // Progress bar must be present (Radix Progress root has role="progressbar")
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('Test 2b: a Currently Reading entry with null pageCount renders page text and no progress bar', async () => {
    vi.mocked(api.get).mockImplementation((_url: string, config: any) => {
      const status = config?.params?.status;
      if (status === 'CURRENTLY_READING') {
        return Promise.resolve({
          data: {
            ...emptyPage,
            content: [currentlyReadingNullPageCount],
            totalElements: 1,
            totalPages: 1,
          },
        });
      }
      return Promise.resolve({ data: emptyPage });
    });

    renderShelfPage();

    fireEvent.click(screen.getByRole('tab', { name: /currently reading/i }));

    await waitFor(() => {
      expect(screen.getByText('The Hobbit')).toBeInTheDocument();
    });

    // Should show text "Page 50" (no progress bar)
    expect(screen.getByText(/page 50/i)).toBeInTheDocument();
    // No progress bar
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('Test 3: a Read entry renders a read-only StarRating (role group aria-label Star rating)', async () => {
    vi.mocked(api.get).mockImplementation((_url: string, config: any) => {
      const status = config?.params?.status;
      if (status === 'READ') {
        return Promise.resolve({
          data: {
            ...emptyPage,
            content: [readEntryWithRating],
            totalElements: 1,
            totalPages: 1,
          },
        });
      }
      return Promise.resolve({ data: emptyPage });
    });

    renderShelfPage();

    // Switch to Read tab — match exactly "Read" (not "Currently Reading")
    const readTab = screen.getAllByRole('tab').find((el) =>
      el.textContent?.trim() === 'Read',
    );
    if (!readTab) throw new Error('Read tab not found');
    fireEvent.click(readTab);

    await waitFor(() => {
      expect(screen.getByText('Dune')).toBeInTheDocument();
    });

    // StarRating in read-only mode renders a group with aria-label "Star rating"
    // No rating buttons (those only appear in interactive mode)
    expect(screen.queryByRole('button', { name: /rate/i })).not.toBeInTheDocument();
    expect(screen.getByRole('group', { name: /star rating/i })).toBeInTheDocument();
  });
});
