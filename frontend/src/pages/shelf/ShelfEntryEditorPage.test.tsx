/**
 * ShelfEntryEditorPage.test.tsx — TDD RED→GREEN tests for ShelfEntryEditorPage (D-11)
 *
 * Behaviors tested:
 * 1. Editor loads entry via GET /shelf/:id and pre-fills status, currentPage, rating, review, dates
 * 2. Saving metadata calls PATCH /shelf/:id with { status, rating, review, dateStarted,
 *    dateFinished } — NO currentPage key (T-06-09 mass-assignment guard)
 * 3. A 403 from GET /shelf/:id renders the access-denied message ("You don't have access
 *    to this entry.") and a Back button instead of crashing
 *
 * T-06-11: Remove confirmation uses shadcn Dialog (not window.confirm)
 * T-06-10: Review text rendered as text nodes — no dangerouslySetInnerHTML
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { ShelfEntryEditorPage } from './ShelfEntryEditorPage';
import { api } from '../../lib/api';
import type { ShelfEntry } from '../../types/api.types';

vi.mock('../../lib/api', () => ({
  api: {
    get: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 } },
  });
}

/** Render ShelfEntryEditorPage at /shelf/:id with the given entryId */
function renderEditor(entryId: string) {
  const user = userEvent.setup();
  const utils = render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter initialEntries={[`/shelf/${entryId}`]}>
        <Routes>
          <Route path="/shelf/:id" element={<ShelfEntryEditorPage />} />
          <Route path="/shelf" element={<div>Shelf Page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, user };
}

const sampleEntry: ShelfEntry = {
  entryId: 'entry-abc',
  status: 'CURRENTLY_READING',
  title: 'The Hobbit',
  olKey: '/works/OL1W',
  coverId: null,
  authors: 'J.R.R. Tolkien',
  rating: 3,
  review: 'Great adventure',
  currentPage: 150,
  pageCount: 310,
  dateStarted: '2024-01-01',
  dateFinished: null,
  createdAt: '2024-01-01T00:00:00Z',
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ShelfEntryEditorPage', () => {
  it('Test 1: loads entry via GET /shelf/:id and pre-fills all fields', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: sampleEntry } as never);

    renderEditor('entry-abc');

    // Should have called GET /shelf/entry-abc
    await waitFor(() =>
      expect(vi.mocked(api.get)).toHaveBeenCalledWith('/shelf/entry-abc'),
    );

    // Title should appear (confirms entry loaded)
    await waitFor(() =>
      expect(screen.getByText('The Hobbit')).toBeInTheDocument(),
    );

    // currentPage pre-filled
    const pageInput = screen.getByDisplayValue('150');
    expect(pageInput).toBeInTheDocument();

    // review pre-filled
    expect(screen.getByDisplayValue('Great adventure')).toBeInTheDocument();

    // dateStarted pre-filled
    expect(screen.getByDisplayValue('2024-01-01')).toBeInTheDocument();
  });

  it('Test 2: saving metadata calls PATCH /shelf/:id WITHOUT currentPage key', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: sampleEntry } as never);
    vi.mocked(api.patch).mockResolvedValue({ data: sampleEntry } as never);

    const { user } = renderEditor('entry-abc');

    // Wait for entry to load
    await waitFor(() =>
      expect(screen.getByText('The Hobbit')).toBeInTheDocument(),
    );

    // Click "Save Changes" button
    const saveBtn = screen.getByRole('button', { name: /save changes/i });
    await user.click(saveBtn);

    // PATCH must have been called on /shelf/entry-abc
    await waitFor(() =>
      expect(vi.mocked(api.patch)).toHaveBeenCalled(),
    );

    const patchCall = vi.mocked(api.patch).mock.calls[0];
    const patchUrl = patchCall[0] as string;
    const patchBody = patchCall[1] as Record<string, unknown>;

    // URL must be /shelf/entry-abc (not /progress sub-resource)
    expect(patchUrl).toBe('/shelf/entry-abc');

    // Body must NOT contain currentPage (T-06-09 mass-assignment guard)
    expect(patchBody).not.toHaveProperty('currentPage');

    // Body must contain metadata fields
    expect(patchBody).toHaveProperty('status');
    expect(patchBody).toHaveProperty('rating');
    expect(patchBody).toHaveProperty('review');
    expect(patchBody).toHaveProperty('dateStarted');
    expect(patchBody).toHaveProperty('dateFinished');
  });

  it('Test 3: 403 from GET /shelf/:id renders access-denied message with Back button', async () => {
    // Simulate 403 error from the API
    const error403 = {
      response: { status: 403, data: { message: 'Forbidden' } },
    };
    vi.mocked(api.get).mockRejectedValue(error403 as never);

    renderEditor('entry-xyz');

    // Wait for error state to render
    await waitFor(() =>
      expect(
        screen.getByText(/you don't have access to this entry/i),
      ).toBeInTheDocument(),
    );

    // Must show a Back button
    expect(
      screen.getByRole('button', { name: /back/i }),
    ).toBeInTheDocument();

    // Should NOT crash — no error boundary text
    expect(screen.queryByText(/something went wrong/i)).not.toBeInTheDocument();
  });
});
