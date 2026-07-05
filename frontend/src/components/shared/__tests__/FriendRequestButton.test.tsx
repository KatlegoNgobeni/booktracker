/**
 * FriendRequestButton.test.tsx — renders all 4 states + mutation invocation
 *
 * Tests:
 * 1. NONE → shows "Add Friend"
 * 2. PENDING_SENT → shows "Request Sent"
 * 3. PENDING_RECEIVED → shows both "Accept" and "Reject"
 * 4. ACCEPTED → shows disabled "Friends"
 * 5. Clicking "Add Friend" invokes the send mutation
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { FriendRequestButton } from '../FriendRequestButton';
import * as useSocialModule from '../../../hooks/useSocial';

// Mock the entire useSocial module so hooks return controlled values
vi.mock('../../../hooks/useSocial', () => ({
  useSendFriendRequest: vi.fn(),
  useCancelFriendRequest: vi.fn(),
  useAcceptFriendRequest: vi.fn(),
  useRejectFriendRequest: vi.fn(),
}));

const mockMutate = vi.fn();

function makeIdleMutation() {
  return { mutate: mockMutate, isPending: false };
}

function makePendingMutation() {
  return { mutate: mockMutate, isPending: true };
}

function makeQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function renderButton(
  props: Parameters<typeof FriendRequestButton>[0],
) {
  return render(
    <QueryClientProvider client={makeQueryClient()}>
      <FriendRequestButton {...props} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  // Default all mutations to idle (non-pending)
  vi.mocked(useSocialModule.useSendFriendRequest).mockReturnValue(
    makeIdleMutation() as ReturnType<typeof useSocialModule.useSendFriendRequest>,
  );
  vi.mocked(useSocialModule.useCancelFriendRequest).mockReturnValue(
    makeIdleMutation() as ReturnType<typeof useSocialModule.useCancelFriendRequest>,
  );
  vi.mocked(useSocialModule.useAcceptFriendRequest).mockReturnValue(
    makeIdleMutation() as ReturnType<typeof useSocialModule.useAcceptFriendRequest>,
  );
  vi.mocked(useSocialModule.useRejectFriendRequest).mockReturnValue(
    makeIdleMutation() as ReturnType<typeof useSocialModule.useRejectFriendRequest>,
  );
});

describe('FriendRequestButton', () => {
  it('shows "Add Friend" when status is NONE', () => {
    renderButton({ userId: 'user-1', status: 'NONE' });
    expect(screen.getByRole('button', { name: /add friend/i })).toBeInTheDocument();
  });

  it('shows "Request Sent" when status is PENDING_SENT', () => {
    renderButton({ userId: 'user-1', requestId: 'req-1', status: 'PENDING_SENT' });
    expect(screen.getByRole('button', { name: /request sent/i })).toBeInTheDocument();
  });

  it('shows "Accept" and "Reject" when status is PENDING_RECEIVED', () => {
    renderButton({ userId: 'user-1', requestId: 'req-1', status: 'PENDING_RECEIVED' });
    expect(screen.getByRole('button', { name: /accept/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reject/i })).toBeInTheDocument();
  });

  it('shows a disabled "Friends" button when status is ACCEPTED', () => {
    renderButton({ userId: 'user-1', status: 'ACCEPTED' });
    const btn = screen.getByRole('button', { name: /friends/i });
    expect(btn).toBeInTheDocument();
    expect(btn).toBeDisabled();
  });

  it('calls the send mutation when "Add Friend" is clicked', () => {
    renderButton({ userId: 'user-1', status: 'NONE' });
    fireEvent.click(screen.getByRole('button', { name: /add friend/i }));
    expect(mockMutate).toHaveBeenCalledTimes(1);
  });
});
