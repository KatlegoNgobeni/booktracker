/**
 * PendingRequestsWidget.test.tsx — renders pending request list and fires accept mutation
 *
 * Tests:
 * 1. Returns nothing (renders null) when there are no pending requests
 * 2. Renders one row per request with Accept and Reject buttons when requests exist
 * 3. Clicking Accept invokes the accept mutation
 * 4. Each row renders the shared UserAvatar seeded by requesterId — initials
 *    fallback visible in jsdom (AVATAR-05, 12-05)
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { PendingRequestsWidget } from '../PendingRequestsWidget';
import * as useSocialModule from '../../../hooks/useSocial';
import type { FriendRequest } from '../../../types/api.types';

vi.mock('../../../hooks/useSocial', () => ({
  usePendingReceivedRequests: vi.fn(),
  useAcceptFriendRequest: vi.fn(),
  useRejectFriendRequest: vi.fn(),
}));

const mockAcceptMutate = vi.fn();
const mockRejectMutate = vi.fn();

const IDLE_MUTATION = { mutate: vi.fn(), isPending: false };
const ACCEPT_MUTATION = { mutate: mockAcceptMutate, isPending: false };
const REJECT_MUTATION = { mutate: mockRejectMutate, isPending: false };

const SAMPLE_REQUESTS: FriendRequest[] = [
  {
    id: 'req-1',
    requesterId: 'user-a',
    recipientId: 'user-me',
    requesterDisplayName: 'Alice',
    status: 'PENDING',
    createdAt: '2026-07-01T10:00:00Z',
  },
  {
    id: 'req-2',
    requesterId: 'user-b',
    recipientId: 'user-me',
    requesterDisplayName: 'Bob Smith',
    status: 'PENDING',
    createdAt: '2026-07-02T10:00:00Z',
  },
];

function makeQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderWidget() {
  return render(
    <QueryClientProvider client={makeQueryClient()}>
      <PendingRequestsWidget />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useSocialModule.useAcceptFriendRequest).mockReturnValue(
    ACCEPT_MUTATION as ReturnType<typeof useSocialModule.useAcceptFriendRequest>,
  );
  vi.mocked(useSocialModule.useRejectFriendRequest).mockReturnValue(
    REJECT_MUTATION as ReturnType<typeof useSocialModule.useRejectFriendRequest>,
  );
});

describe('PendingRequestsWidget', () => {
  it('renders nothing when there are no pending requests', () => {
    vi.mocked(useSocialModule.usePendingReceivedRequests).mockReturnValue({
      data: [],
    } as ReturnType<typeof useSocialModule.usePendingReceivedRequests>);

    const { container } = renderWidget();
    expect(container.firstChild).toBeNull();
  });

  it('renders one row per request with Accept and Reject buttons', () => {
    vi.mocked(useSocialModule.usePendingReceivedRequests).mockReturnValue({
      data: SAMPLE_REQUESTS,
    } as ReturnType<typeof useSocialModule.usePendingReceivedRequests>);

    renderWidget();

    // Both names should appear with the "wants to be your friend" text
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob Smith')).toBeInTheDocument();

    // Two Accept and two Reject buttons (one per request row)
    const acceptBtns = screen.getAllByRole('button', { name: /accept/i });
    const rejectBtns = screen.getAllByRole('button', { name: /reject/i });
    expect(acceptBtns).toHaveLength(2);
    expect(rejectBtns).toHaveLength(2);
  });

  it('calls the accept mutation when Accept is clicked', () => {
    vi.mocked(useSocialModule.usePendingReceivedRequests).mockReturnValue({
      data: [SAMPLE_REQUESTS[0]],
    } as ReturnType<typeof useSocialModule.usePendingReceivedRequests>);

    renderWidget();

    fireEvent.click(screen.getByRole('button', { name: /accept/i }));
    expect(mockAcceptMutate).toHaveBeenCalledTimes(1);
  });

  it('renders the UserAvatar initials fallback per requester row (AVATAR-05)', () => {
    vi.mocked(useSocialModule.usePendingReceivedRequests).mockReturnValue({
      data: SAMPLE_REQUESTS,
    } as ReturnType<typeof useSocialModule.usePendingReceivedRequests>);

    renderWidget();

    // jsdom never fires image load, so the radix fallback shows initials
    expect(screen.getByText('A')).toBeInTheDocument(); // Alice
    expect(screen.getByText('BS')).toBeInTheDocument(); // Bob Smith
  });
});
