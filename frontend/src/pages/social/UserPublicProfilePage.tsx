/**
 * UserPublicProfilePage.tsx — Public profile at /users/:id (SOCIAL-02)
 *
 * Displays: display name, follower/following counts, goal progress, READ shelf grid.
 *
 * Security:
 * - T-08F-01: Renders only fields the backend returns (READ entries only).
 *   Backend enforces visibility; client never fetches WANT_TO_READ / CURRENTLY_READING.
 * - T-08F-03: Follow button hidden when profile.userId equals current-user id (Pitfall 5).
 *
 * The FollowButton parent-visibility contract:
 *   Only render <FollowButton> when the current user is NOT viewing their own profile.
 *   The button itself has no self-awareness; the page makes the decision.
 */
import { useParams } from 'react-router-dom';
import { Link } from 'react-router-dom';
import { usePublicProfile, useCurrentUserId } from '../../hooks/useSocial';
import { FollowButton } from '../../components/shared/FollowButton';
import { BookCoverImage } from '../../components/shared/BookCoverImage';
import { StarRating } from '../../components/shared/StarRating';
import type { PublicShelfEntry } from '../../types/api.types';

// ────────────────────────────────────────────────────────
// Sub-components
// ────────────────────────────────────────────────────────

function ProfileHeader({
  displayName,
  followerCount,
  followingCount,
  goalTarget,
  goalProgressPercent,
  booksReadThisYear,
  userId,
  isFollowing,
  showFollowButton,
}: {
  displayName: string;
  followerCount: number;
  followingCount: number;
  goalTarget?: number;
  goalProgressPercent?: number;
  booksReadThisYear: number;
  userId: string;
  isFollowing: boolean;
  showFollowButton: boolean;
}) {
  return (
    <div className="flex flex-col gap-3 px-4 pt-4 pb-3 border-b">
      <div className="flex items-start justify-between gap-3">
        <h1 className="text-xl font-semibold leading-tight">{displayName}</h1>
        {showFollowButton && (
          <FollowButton userId={userId} isFollowing={isFollowing} />
        )}
      </div>

      {/* Follower / Following counts */}
      <div className="flex gap-4 text-sm text-muted-foreground">
        <span>
          <strong className="text-foreground">{followerCount}</strong> followers
        </span>
        <span>
          <strong className="text-foreground">{followingCount}</strong> following
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

function ReadBookCard({ entry }: { entry: PublicShelfEntry }) {
  return (
    <div className="flex flex-col gap-1">
      <Link to={`/books/${entry.olKey}`}>
        <BookCoverImage
          key={entry.coverId ?? entry.olKey}
          coverId={entry.coverId}
          title={entry.title}
          className="w-full aspect-[2/3] rounded object-cover"
        />
      </Link>
      {entry.rating !== null && (
        <StarRating value={entry.rating} readOnly />
      )}
      {entry.review && (
        <p className="text-xs text-muted-foreground line-clamp-2">{entry.review}</p>
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
  // Used to hide the Follow button on own profile (RESEARCH Pitfall 5)
  const { data: currentUserId } = useCurrentUserId();

  if (isPending) {
    return (
      <div className="px-4 pt-4">
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

  // T-08F-03: Hide Follow button when viewing own profile (Pitfall 5)
  const showFollowButton = !!currentUserId && currentUserId !== profile.userId;
  const readEntries = profile.readEntries.content;

  return (
    <div className="pb-4">
      <ProfileHeader
        displayName={profile.displayName}
        followerCount={profile.followerCount}
        followingCount={profile.followingCount}
        goalTarget={profile.goalTarget}
        goalProgressPercent={profile.goalProgressPercent}
        booksReadThisYear={profile.booksReadThisYear}
        userId={profile.userId}
        isFollowing={profile.isFollowing}
        showFollowButton={showFollowButton}
      />

      <div className="px-4 pt-4">
        {readEntries.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            No finished books yet.
          </p>
        ) : (
          <div className="grid grid-cols-3 gap-3">
            {readEntries.map((entry) => (
              <ReadBookCard key={entry.entryId} entry={entry} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
