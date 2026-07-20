/**
 * PendingRequestsWidget.tsx — pending incoming friend-request section (DISC-03, D-09, D-10)
 *
 * Rendered at the top of FeedPage (above the Feed heading). Returns null when there are
 * no pending requests so no empty-state card is shown (D-10).
 *
 * After Accept/Reject the item disappears via TanStack Query invalidation of the
 * pendingReceived key — no confirmation dialog required (D-09).
 *
 * Security:
 * - T-09-16: Requester display names rendered via JSX text interpolation only;
 *   no dangerouslySetInnerHTML.
 * - T-09-17: Accept/reject mutations send no userId in the body; identity comes from JWT.
 */
import { Button } from '../ui/button';
import { Separator } from '../ui/separator';
import { UserAvatar } from '../shared/UserAvatar';
import {
  usePendingReceivedRequests,
  useAcceptFriendRequest,
  useRejectFriendRequest,
} from '../../hooks/useSocial';

// ────────────────────────────────────────────────────────
// Single pending-request row
// ────────────────────────────────────────────────────────

function PendingRequestRow({
  requestId,
  requesterId,
  displayName,
  photoUrl,
}: {
  requestId: string;
  /** Requester's user UUID — seeds the generated avatar (AVATAR-05). */
  requesterId: string;
  displayName: string;
  photoUrl?: string | null;
}) {
  const acceptMutation = useAcceptFriendRequest(requestId);
  const rejectMutation = useRejectFriendRequest(requestId);
  const isPending = acceptMutation.isPending || rejectMutation.isPending;

  return (
    <div className="flex items-center gap-3 px-4 py-2 border-b last:border-b-0">
      {/* AVATAR-05: generated avatar seeded by requester UUID; T-09-16 — initials
          fallback derives from displayName via string split, no HTML injection */}
      <UserAvatar userId={requesterId} displayName={displayName} size="sm" photoUrl={photoUrl ?? null} />
      <p className="flex-1 text-sm text-foreground min-w-0 truncate">
        <strong className="font-semibold">{displayName}</strong>
        {' '}wants to be your friend
      </p>
      <div className="flex gap-2 shrink-0">
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
    </div>
  );
}

// ────────────────────────────────────────────────────────
// PendingRequestsWidget
// ────────────────────────────────────────────────────────

export function PendingRequestsWidget() {
  const { data: requests } = usePendingReceivedRequests();

  // D-10: return null when empty — no empty-state card
  if (!requests || requests.length === 0) {
    return null;
  }

  return (
    <section aria-label="Friend requests" className="mb-4">
      <h2 className="text-sm font-semibold text-foreground mb-2 px-4">Friend Requests</h2>
      <div className="flex flex-col">
        {requests.map((req) => (
          <PendingRequestRow
            key={req.id}
            requestId={req.id}
            requesterId={req.requesterId}
            displayName={req.requesterDisplayName}
            photoUrl={req.requesterPhotoUrl ?? null}
          />
        ))}
      </div>
      <Separator className="mt-2" />
    </section>
  );
}
