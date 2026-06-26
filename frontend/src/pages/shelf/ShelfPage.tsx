/**
 * ShelfPage.tsx — Tabbed shelf view (Want to Read / Currently Reading / Read)
 *
 * D-09: Three status tabs with infinite-scrolling paginated entries.
 * D-10: CURRENTLY_READING cards show a progress bar only when entry.pageCount is present
 *       and > 0; otherwise show "Page {n}" text. READ cards show a read-only StarRating.
 * T-06-10: Review text is rendered as React text nodes — no dangerouslySetInnerHTML.
 *
 * Architecture note: Radix UI Tabs renders all three TabsContent components at once
 * (active is shown, others are hidden). All three useShelfList hooks therefore fire
 * on initial mount so the cache is warm when the user switches tabs.
 */
import { useNavigate } from 'react-router-dom';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../../components/ui/tabs';
import { Card, CardContent } from '../../components/ui/card';
import { Progress } from '../../components/ui/progress';
import { Button } from '../../components/ui/button';
import { BookCoverImage } from '../../components/shared/BookCoverImage';
import { StarRating } from '../../components/shared/StarRating';
import { useShelfList } from '../../hooks/useShelf';
import type { ShelfEntry, ShelfStatus } from '../../types/api.types';

// ────────────────────────────────────────────────────────
// Status-specific card content (D-10)
// ────────────────────────────────────────────────────────

function ShelfEntryCard({
  entry,
  onClick,
}: {
  entry: ShelfEntry;
  onClick: () => void;
}) {
  return (
    <Card
      className="cursor-pointer hover:bg-accent/5 transition-colors"
      onClick={onClick}
    >
      <CardContent className="flex gap-3 p-3">
        {/* Cover image — keyed by coverId to remount on cover change (D-12 pitfall) */}
        <BookCoverImage
          key={entry.coverId ?? entry.olKey}
          coverId={entry.coverId}
          title={entry.title}
          className="w-12 h-16 rounded object-cover flex-shrink-0"
        />

        <div className="flex flex-col justify-center gap-1 min-w-0">
          <p className="text-base font-semibold leading-tight truncate">
            {entry.title}
          </p>
          {entry.authors && (
            <p className="text-sm text-muted-foreground truncate">
              {entry.authors}
            </p>
          )}

          {/* Status-specific info (D-10) */}
          {entry.status === 'CURRENTLY_READING' && (
            <CurrentlyReadingInfo entry={entry} />
          )}
          {entry.status === 'READ' && (
            <StarRating value={entry.rating} readOnly />
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function CurrentlyReadingInfo({ entry }: { entry: ShelfEntry }) {
  const hasPageCount =
    entry.pageCount != null && entry.pageCount > 0;

  if (hasPageCount) {
    const progress = Math.min(
      100,
      Math.max(0, ((entry.currentPage ?? 0) / entry.pageCount!) * 100),
    );
    return (
      <div className="flex flex-col gap-1">
        <Progress value={progress} className="h-1.5" />
        <p className="text-xs text-muted-foreground">
          {entry.currentPage ?? 0} / {entry.pageCount} pages
        </p>
      </div>
    );
  }

  // No page count — show text-only variant
  if (entry.currentPage) {
    return (
      <p className="text-xs text-muted-foreground">
        Page {entry.currentPage}
      </p>
    );
  }

  return null;
}

// ────────────────────────────────────────────────────────
// Per-tab panel (infinite scroll list + empty/error states)
// ────────────────────────────────────────────────────────

function ShelfTabPanel({ status }: { status: ShelfStatus }) {
  const navigate = useNavigate();
  const {
    data,
    isPending,
    isError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useShelfList(status);

  const entries = data?.pages.flatMap((p) => p.content) ?? [];

  if (isPending) {
    return (
      <div className="flex flex-col gap-3">
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="h-24 rounded-lg bg-muted animate-pulse"
            aria-label="Loading"
          />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center gap-3 py-8 text-center">
        <p className="text-sm text-muted-foreground">
          Couldn&apos;t load your shelf. Try again.
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          Retry
        </Button>
      </div>
    );
  }

  if (entries.length === 0) {
    return <ShelfEmptyState status={status} />;
  }

  return (
    <div className="flex flex-col gap-3">
      {entries.map((entry) => (
        <ShelfEntryCard
          key={entry.entryId}
          entry={entry}
          onClick={() => navigate(`/shelf/${entry.entryId}/edit`)}
        />
      ))}

      {hasNextPage && (
        <Button
          variant="outline"
          className="w-full"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
        >
          {isFetchingNextPage ? 'Loading…' : 'Load more'}
        </Button>
      )}
    </div>
  );
}

function ShelfEmptyState({ status }: { status: ShelfStatus }) {
  const copy: Record<ShelfStatus, { heading: string; body: string }> = {
    WANT_TO_READ: {
      heading: 'Nothing to read yet',
      body: 'Add books you want to read and find them here.',
    },
    CURRENTLY_READING: {
      heading: 'Not reading anything',
      body: "Move a book to 'Currently Reading' to track your progress.",
    },
    READ: {
      heading: 'No books finished yet',
      body: 'Finish a book to see your stats and leave a review.',
    },
  };

  return (
    <div className="flex flex-col items-center gap-2 py-12 text-center">
      <p className="text-base font-semibold">{copy[status].heading}</p>
      <p className="text-sm text-muted-foreground max-w-xs">{copy[status].body}</p>
    </div>
  );
}

// ────────────────────────────────────────────────────────
// ShelfPage
// ────────────────────────────────────────────────────────

export function ShelfPage() {
  return (
    <div className="px-4 pt-4 pb-4">
      <h1 className="text-xl font-semibold mb-4">My Shelf</h1>

      <Tabs defaultValue="WANT_TO_READ">
        <TabsList className="w-full mb-4">
          <TabsTrigger value="WANT_TO_READ" className="flex-1">
            Want to Read
          </TabsTrigger>
          <TabsTrigger value="CURRENTLY_READING" className="flex-1">
            Currently Reading
          </TabsTrigger>
          <TabsTrigger value="READ" className="flex-1">
            Read
          </TabsTrigger>
        </TabsList>

        <TabsContent value="WANT_TO_READ">
          <ShelfTabPanel status="WANT_TO_READ" />
        </TabsContent>
        <TabsContent value="CURRENTLY_READING">
          <ShelfTabPanel status="CURRENTLY_READING" />
        </TabsContent>
        <TabsContent value="READ">
          <ShelfTabPanel status="READ" />
        </TabsContent>
      </Tabs>
    </div>
  );
}
