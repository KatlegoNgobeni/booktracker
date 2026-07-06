/**
 * FriendRequestButton.tsx — 4-state friend request action button (DISC-02, D-06)
 *
 * Renders the correct control for each friendship state:
 *   NONE            → "Add Friend" (send request)
 *   PENDING_SENT    → "Request Sent" (cancel request)
 *   PENDING_RECEIVED → "Accept" + "Reject" inline
 *   ACCEPTED        → "Friends" (disabled, no action)
 *
 * Security:
 * - T-09-17: Actor identity comes from the JWT interceptor — no userId is passed in the
 *   request body. Only the requestId (for cancel/accept/reject) goes in the URL path.
 * - T-09-18: Parent is responsible for NOT rendering this component when
 *   userId === currentUserId (self-guard, same contract as FollowButton).
 */
import { Button } from '../ui/button';
import {
  useSendFriendRequest,
  useCancelFriendRequest,
  useAcceptFriendRequest,
  useRejectFriendRequest,
} from '../../hooks/useSocial';
import type { FriendStatus } from '../../types/api.types';

interface Props {
  userId: string;
  requestId?: string;
  status: FriendStatus;
}

export function FriendRequestButton({ userId, requestId, status }: Props) {
  const sendMutation = useSendFriendRequest(userId);
  const cancelMutation = useCancelFriendRequest(requestId ?? '');
  const acceptMutation = useAcceptFriendRequest(requestId ?? '');
  const rejectMutation = useRejectFriendRequest(requestId ?? '');

  if (status === 'ACCEPTED') {
    return (
      <Button variant="outline" size="sm" disabled>
        Friends
      </Button>
    );
  }

  if (status === 'PENDING_SENT') {
    return (
      <Button
        variant="outline"
        size="sm"
        disabled={cancelMutation.isPending}
        onClick={() => cancelMutation.mutate()}
      >
        {cancelMutation.isPending ? 'Cancelling…' : 'Request Sent'}
      </Button>
    );
  }

  if (status === 'PENDING_RECEIVED') {
    const isPending = acceptMutation.isPending || rejectMutation.isPending;
    return (
      <div className="flex gap-2">
        <Button
          variant="default"
          size="sm"
          disabled={isPending}
          onClick={() => acceptMutation.mutate()}
        >
          {acceptMutation.isPending ? 'Accepting…' : 'Accept'}
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={isPending}
          onClick={() => rejectMutation.mutate()}
        >
          {rejectMutation.isPending ? 'Rejecting…' : 'Reject'}
        </Button>
      </div>
    );
  }

  // NONE — default: "Add Friend"
  return (
    <Button
      variant="default"
      size="sm"
      disabled={sendMutation.isPending}
      onClick={() => sendMutation.mutate()}
    >
      {sendMutation.isPending ? 'Sending…' : 'Add Friend'}
    </Button>
  );
}
