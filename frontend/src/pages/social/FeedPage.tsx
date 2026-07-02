/**
 * FeedPage.tsx — Activity feed at /feed (SOCIAL-03)
 *
 * Renders an infinite-scroll list of recent book finishes from followed users.
 * Each item links: book title → /books/:olKey; reader name → /users/:userId.
 * Relative timestamps via formatRelativeDate (RESEARCH Pattern 8).
 *
 * Infinite scroll mirrors ShelfPage: useInfiniteQuery + "Load more" button.
 * Empty state is shown when the user follows no one or followees have no finishes.
 *
 * Security:
 * - T-08F-02: Feed is scoped server-side to the authenticated user's followees;
 *   no userId is sent from the client (backend uses JWT identity).
 */
import { Link } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { BookCoverImage } from '../../components/shared/BookCoverImage';
import { StarRating } from '../../components/shared/StarRating';
import { useFeed } from '../../hooks/useSocial';
import { formatRelativeDate } from '../../lib/utils';
import type { FeedItem } from '../../types/api.types';

// ────────────────────────────────────────────────────────
// Sub-components
// ────────────────────────────────────────────────────────

function FeedItemCard({ item }: { item: FeedItem }) {
  return (
    <div className="flex gap-3 py-3 border-b last:border-b-0">
      {/* Book cover — links to book detail */}
      <Link to={`/books/${item.bookOlKey}`} className="flex-shrink-0">
        <BookCoverImage
          key={item.bookCoverId ?? item.bookOlKey}
          coverId={item.bookCoverId}
          title={item.bookTitle}
          className="w-12 h-16 rounded object-cover"
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

// ────────────────────────────────────────────────────────
// FeedPage
// ────────────────────────────────────────────────────────

export function FeedPage() {
  const {
    data,
    isPending,
    isError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useFeed();

  // Flatten all pages into a single item list
  const items = data?.pages.flatMap((p) => p.content) ?? [];

  if (isPending) {
    return (
      <div className="px-4 pt-4 flex flex-col gap-3">
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

  if (isError) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 px-4 text-center">
        <p className="text-sm text-muted-foreground">
          Couldn&apos;t load the feed. Try again.
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          Retry
        </Button>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 px-4 text-center">
        <p className="text-base font-semibold">No activity yet</p>
        <p className="text-sm text-muted-foreground max-w-xs">
          Follow some readers to see their recent finishes here.
        </p>
      </div>
    );
  }

  return (
    <div className="px-4 pt-4 pb-4">
      <h1 className="text-xl font-semibold mb-3">Feed</h1>

      <div className="flex flex-col">
        {items.map((item) => (
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
    </div>
  );
}
