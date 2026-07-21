/**
 * FeedPage.tsx — Letterboxd-style discovery surface at /feed (DISC-01 through DISC-05)
 *
 * Renders three stacked discovery sections above the existing activity feed:
 *   1. "Friends are reading"   — books friends currently have open (DISC-01)
 *   2. "Trending this week"    — curated static list of popular titles (DISC-05)
 *   3. "Up next for you"       — user's WANT_TO_READ shelf (conditional, DISC-01)
 *   4. "Friends finished recently" — original infinite-scroll activity feed (DISC-04)
 *
 * Each discovery book card is a Link to /books/:olKey (DISC-02).
 * Discovery card rows use overflow-x-auto + snap-x snap-mandatory for mobile-safe
 * horizontal scrolling without page-level overflow (DISC-05).
 *
 * Security:
 * - T-08F-02: Feed is scoped server-side to the authenticated user's followees
 * - T-15-05: All title/displayName rendered via JSX text interpolation — no dangerouslySetInnerHTML
 * - T-15-06: FeedItemCard uses JSX text throughout (pre-existing mitigation from Phase 8)
 */
import { Link } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { BookCoverImage } from '../../components/shared/BookCoverImage';
import { StarRating } from '../../components/shared/StarRating';
import { UserAvatar } from '../../components/shared/UserAvatar';
import { PendingRequestsWidget } from '../../components/social/PendingRequestsWidget';
import { DiscoverySection } from '../../components/social/DiscoverySection';
import { DiscoveryBookCard } from '../../components/social/DiscoveryBookCard';
import { useFeed, useFriendsReading } from '../../hooks/useSocial';
import { useShelfList } from '../../hooks/useShelf';
import { useStats } from '../../hooks/useStats';
import { useBooksByGenre } from '../../hooks/useBooks';
import { CURATED_TRENDING } from '../../lib/trending';
import { formatRelativeDate } from '../../lib/utils';
import type { FeedItem } from '../../types/api.types';

// ────────────────────────────────────────────────────────
// Sub-components
// ────────────────────────────────────────────────────────

function FeedItemCard({ item }: { item: FeedItem }) {
  return (
    <div className="flex gap-3 py-3 border-b last:border-b-0">
      {/* AVATAR-03: reader's generated avatar leads each feed row (32px, gap-3 per UI-SPEC).
          Non-interactive decoration — the name Link below handles navigation. */}
      <UserAvatar
        userId={item.userId}
        displayName={item.displayName}
        size="default"
        className="shrink-0"
        photoUrl={item.photoUrl ?? null}
      />

      {/* Book cover — links to book detail */}
      <Link to={`/books/${item.bookOlKey}`} className="flex-shrink-0">
        <BookCoverImage
          key={item.bookCoverId ?? item.bookOlKey}
          coverId={item.bookCoverId}
          title={item.bookTitle}
          className="aspect-[2/3] w-12 object-cover rounded-md"
        />
      </Link>

      <div className="flex flex-col justify-center gap-1 min-w-0">
        {/* Reader name → profile */}
        <Link
          to={`/users/${item.userId}`}
          className="text-sm font-semibold text-foreground hover:underline truncate"
        >
          {item.displayName}
        </Link>

        {/* Book title → book detail */}
        <Link
          to={`/books/${item.bookOlKey}`}
          className="text-sm text-muted-foreground hover:underline truncate"
        >
          {item.bookTitle}
        </Link>

        {/* Authors */}
        {item.bookAuthors && (
          <p className="text-xs text-muted-foreground truncate">{item.bookAuthors}</p>
        )}

        {/* Rating (read-only stars) — only when rated */}
        {item.rating !== null && (
          <StarRating value={item.rating} readOnly />
        )}

        {/* Review snippet — T-06-10: plain text, no dangerouslySetInnerHTML */}
        {item.review && (
          <p className="text-xs text-muted-foreground line-clamp-2">{item.review}</p>
        )}

        {/* Relative timestamp */}
        <p className="text-xs text-muted-foreground">
          {formatRelativeDate(item.dateFinished)}
        </p>
      </div>
    </div>
  );
}

// Inline skeleton row for discovery sections still loading
function InlineSkeletonRow() {
  return (
    <>
      {[1, 2, 3, 4].map((i) => (
        <div
          key={i}
          className="flex-shrink-0 w-24 snap-start"
        >
          <div className="aspect-[2/3] w-24 rounded-md bg-muted animate-pulse" />
          <div className="h-3 mt-1 rounded bg-muted animate-pulse w-16" />
        </div>
      ))}
    </>
  );
}

// ────────────────────────────────────────────────────────
// FeedPage
// ────────────────────────────────────────────────────────

export function FeedPage() {
  // Activity feed (infinite scroll)
  const {
    data: feedData,
    isPending: feedIsPending,
    isError: feedIsError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useFeed();

  // Discovery: Friends are reading
  const {
    data: friendsReadingData,
    isPending: friendsReadingPending,
    isError: friendsReadingError,
  } = useFriendsReading();

  // Discovery: Up next for you (WANT_TO_READ shelf)
  const { data: wantToReadData } = useShelfList('WANT_TO_READ');

  // Optional: topGenre for subtitle text on "Up next" section
  const { data: statsData } = useStats();

  // Flatten all activity feed pages
  const feedItems = feedData?.pages.flatMap((p) => p.content) ?? [];

  // Friends currently reading
  const friendsReadingItems = friendsReadingData?.content ?? [];

  // WANT_TO_READ shelf entries (first 10)
  const wantToReadItems = wantToReadData?.pages?.flatMap((p) => p.content) ?? [];

  // topGenre from stats; fall back to 'Fiction' when no subject data exists for the user's reads
  const topGenre = statsData?.topGenre;
  const effectiveGenre = topGenre ?? 'Fiction';

  // Genre-based recommendations — always fires (uses effectiveGenre as fallback)
  const { data: genreRecsData, isPending: genreRecsPending } = useBooksByGenre(effectiveGenre);

  // Full-page loading state: only block render if both discovery + feed are pending
  if (feedIsPending && friendsReadingPending) {
    return (
      <div className="flex flex-col gap-3">
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="h-20 rounded-lg bg-muted animate-pulse"
            aria-label="Loading"
          />
        ))}
      </div>
    );
  }

  return (
    <div className="pb-4">
      {/* D-10: hidden when no pending requests; returns null when empty */}
      <PendingRequestsWidget />
      <h1 className="text-[28px] font-semibold mb-4">Feed</h1>

      {/* Discovery: Friends are reading (DISC-01) */}
      <DiscoverySection title="Friends are reading">
        {friendsReadingPending ? (
          <InlineSkeletonRow />
        ) : friendsReadingError ? (
          <p className="text-xs text-muted-foreground py-2 px-1 flex-shrink-0">
            Couldn&apos;t load friends&apos; reading activity. Pull to refresh.
          </p>
        ) : friendsReadingItems.length > 0 ? (
          friendsReadingItems.map((item) => (
            <DiscoveryBookCard
              key={item.entryId}
              title={item.bookTitle}
              coverId={item.bookCoverId ?? null}
              olKey={item.bookOlKey}
            />
          ))
        ) : (
          <p className="text-xs text-muted-foreground py-2 px-1 flex-shrink-0">
            No friends reading yet. Add friends to see what they&apos;re reading.
          </p>
        )}
      </DiscoverySection>

      {/* Discovery: Trending this week (DISC-05) */}
      <DiscoverySection title="Trending this week">
        {CURATED_TRENDING.map((book) => (
          <DiscoveryBookCard
            key={book.olKey}
            title={book.title}
            coverId={book.coverId}
            olKey={book.olKey}
          />
        ))}
      </DiscoverySection>

      {/* Discovery: Up next for you — conditional on WANT_TO_READ shelf having items */}
      {wantToReadItems.length > 0 && (
        <DiscoverySection
          title="Up next for you"
          subtitle={topGenre ? `Based on your interest in ${topGenre}` : undefined}
        >
          {wantToReadItems.slice(0, 10).map((item) => (
            <DiscoveryBookCard
              key={item.entryId}
              title={item.title}
              coverId={item.coverId}
              olKey={item.olKey}
            />
          ))}
        </DiscoverySection>
      )}

      {/* Discovery: Recommended for you — always shown; uses topGenre or Fiction fallback */}
      <DiscoverySection
        title="Recommended for you"
        subtitle={topGenre ? `Because you like ${topGenre}` : 'Popular picks'}
      >
          {genreRecsPending ? (
            <InlineSkeletonRow />
          ) : (genreRecsData ?? []).length > 0 ? (
            (genreRecsData ?? []).map((book) => (
              <DiscoveryBookCard
                key={book.olKey}
                title={book.title}
                coverId={book.coverId}
                olKey={book.olKey}
              />
            ))
          ) : (
            <p className="text-xs text-muted-foreground py-2 px-1 flex-shrink-0">
              No recommendations available right now.
            </p>
          )}
        </DiscoverySection>

      {/* Divider */}
      <div className="border-t my-4" />

      {/* Activity feed: Friends finished recently (DISC-04) */}
      <h2 className="text-base font-semibold mb-3">Friends finished recently</h2>

      {feedIsError ? (
        <div className="flex flex-col items-center gap-3 py-16 px-4 text-center">
          <p className="text-sm text-muted-foreground">
            Couldn&apos;t load the feed. Try again.
          </p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            Retry
          </Button>
        </div>
      ) : feedItems.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-8 px-4 text-center">
          <p className="text-base font-semibold">No activity yet</p>
          <p className="text-sm text-muted-foreground max-w-xs">
            Add friends and finish books to see activity here.
          </p>
        </div>
      ) : (
        <>
          <div className="flex flex-col">
            {feedItems.map((item) => (
              <FeedItemCard key={item.entryId} item={item} />
            ))}
          </div>

          {hasNextPage && (
            <Button
              variant="outline"
              className="w-full mt-4"
              onClick={() => fetchNextPage()}
              disabled={isFetchingNextPage}
            >
              {isFetchingNextPage ? 'Loading…' : 'Load more'}
            </Button>
          )}
        </>
      )}
    </div>
  );
}
