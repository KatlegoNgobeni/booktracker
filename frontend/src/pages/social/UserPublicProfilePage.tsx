/**
 * UserPublicProfilePage.tsx — Public profile at /users/:id (SOCIAL-02)
 *
 * Displays: display name, friend count, goal progress, READ shelf grid.
 *
 * Security:
 * - T-08F-01: Renders only fields the backend returns (READ entries only).
 *   Backend enforces visibility; client never fetches WANT_TO_READ / CURRENTLY_READING.
 */
import { useParams } from 'react-router-dom';
import { Link } from 'react-router-dom';
import { Heart } from 'lucide-react';
import { usePublicProfile, useLikeReview, useUnlikeReview } from '../../hooks/useSocial';
import { BookCoverImage } from '../../components/shared/BookCoverImage';
import { StarRating } from '../../components/shared/StarRating';
import { UserAvatar } from '../../components/shared/UserAvatar';
import { cn } from '../../lib/utils';
import type { PublicShelfEntry } from '../../types/api.types';

// ────────────────────────────────────────────────────────
// Sub-components
// ────────────────────────────────────────────────────────

function ProfileHeader({
  displayName,
  friendCount,
  goalTarget,
  goalProgressPercent,
  booksReadThisYear,
  userId,
  photoUrl,
}: {
  displayName: string;
  friendCount: number;
  goalTarget?: number;
  goalProgressPercent?: number;
  booksReadThisYear: number;
  userId: string;
  photoUrl?: string | null;
}) {
  return (
    <div className="flex flex-col gap-3 pb-3 border-b">
      <div className="flex items-start justify-between gap-3">
        {/* AVATAR-02: generated avatar (40px) leads the display name, gap-3 (UI-SPEC) */}
        <div className="flex items-center gap-3 min-w-0">
          <UserAvatar userId={userId} displayName={displayName} size="lg" photoUrl={photoUrl ?? null} />
          <h1 className="text-xl font-semibold leading-tight">{displayName}</h1>
        </div>
      </div>

      {/* Friend count */}
      <div className="flex gap-4 text-sm text-muted-foreground">
        <span>
          <strong className="text-foreground">{friendCount}</strong> friends
        </span>
      </div>

      {/* Goal progress */}
      {goalTarget !== undefined && goalProgressPercent !== undefined ? (
        <p className="text-sm text-muted-foreground">
          Reading goal:{' '}
          <strong className="text-foreground">
            {Math.round(goalProgressPercent)}%
          </strong>{' '}
          of {goalTarget} books this year ({booksReadThisYear} read)
        </p>
      ) : (
        <p className="text-sm text-muted-foreground">No goal set</p>
      )}
    </div>
  );
}

function ReadBookCard({
  entry,
  profileUserId,
}: {
  entry: PublicShelfEntry;
  profileUserId: string;
}) {
  const liked = entry.likedByMe;
  const likeMutation = useLikeReview(entry.entryId, profileUserId);
  const unlikeMutation = useUnlikeReview(entry.entryId, profileUserId);
  const isPending = likeMutation.isPending || unlikeMutation.isPending;

  function handleToggle() {
    if (liked) {
      unlikeMutation.mutate();
    } else {
      likeMutation.mutate();
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <Link to={`/books/${entry.olKey}`}>
        <BookCoverImage
          key={entry.coverId ?? entry.olKey}
          coverId={entry.coverId}
          title={entry.title}
          className="w-full aspect-[2/3] object-cover rounded-md"
        />
      </Link>
      {entry.rating !== null && (
        <StarRating value={entry.rating} readOnly />
      )}
      {entry.review && (
        <>
          {/* T-09-16: review text via JSX — no dangerouslySetInnerHTML */}
          <p className="text-xs text-muted-foreground line-clamp-2">{entry.review}</p>
          {/* Like button — only shown when entry has a review (DISC-04) */}
          <button
            aria-label={liked ? 'Unlike review' : 'Like review'}
            aria-pressed={liked}
            className="flex items-center gap-1 text-xs text-muted-foreground
                       hover:text-foreground transition-colors disabled:opacity-50"
            disabled={isPending}
            onClick={handleToggle}
          >
            <Heart
              className={cn(
                'h-4 w-4',
                liked ? 'fill-current text-destructive' : 'text-muted-foreground',
              )}
              aria-hidden="true"
            />
            <span>{entry.likeCount}</span>
          </button>
        </>
      )}
    </div>
  );
}

// ────────────────────────────────────────────────────────
// UserPublicProfilePage
// ────────────────────────────────────────────────────────

export function UserPublicProfilePage() {
  const { id = '' } = useParams<{ id: string }>();
  const { data: profile, isPending, isError } = usePublicProfile(id);

  if (isPending) {
    return (
      <div>
        <div className="h-32 rounded-lg bg-muted animate-pulse mb-4" aria-label="Loading profile" />
        <div className="grid grid-cols-3 gap-3">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} className="aspect-[2/3] rounded bg-muted animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  if (isError || !profile) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 px-4 text-center">
        <p className="text-base font-semibold">User not found</p>
        <p className="text-sm text-muted-foreground">
          This profile doesn&apos;t exist or has been removed.
        </p>
      </div>
    );
  }

  const readEntries = profile.readEntries.content;

  return (
    <div className="pb-4">
      <ProfileHeader
        displayName={profile.displayName}
        friendCount={profile.friendCount}
        goalTarget={profile.goalTarget}
        goalProgressPercent={profile.goalProgressPercent}
        booksReadThisYear={profile.booksReadThisYear}
        userId={profile.userId}
        photoUrl={profile.photoUrl ?? null}
      />

      <div>
        {readEntries.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            No finished books yet.
          </p>
        ) : (
          <div className="grid grid-cols-3 gap-3">
            {readEntries.map((entry) => (
              <ReadBookCard key={entry.entryId} entry={entry} profileUserId={profile.userId} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
