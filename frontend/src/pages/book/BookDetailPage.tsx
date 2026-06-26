/**
 * BookDetailPage.tsx — Book detail view with add-to-shelf and D-16 already-on-shelf state
 *
 * Features:
 * - D-15: Three add-to-shelf buttons (Want to Read / Currently Reading / Read)
 * - D-16: When book is already on shelf, buttons are replaced by status badge + "View on Shelf" link
 * - T-06-05: description rendered as React text node — dangerouslySetInnerHTML is NEVER used
 * - Page count only shown when not null
 *
 * Route: /books/:olKey (olKey is URL-encoded — decodeURIComponent applied on read)
 */
import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Badge } from '../../components/ui/badge';
import { BookCoverImage } from '../../components/shared/BookCoverImage';
import { useBookDetail, useAddToShelf, useShelfEntryForBook } from '../../hooks/useBooks';
import type { ShelfStatus, ShelfEntry } from '../../types/api.types';

const STATUS_LABELS: Record<ShelfStatus, string> = {
  WANT_TO_READ: 'Want to Read',
  CURRENTLY_READING: 'Currently Reading',
  READ: 'Read',
};

const ADD_STATUSES: ShelfStatus[] = ['WANT_TO_READ', 'CURRENTLY_READING', 'READ'];

export function BookDetailPage() {
  const { olKey: encodedOlKey } = useParams<{ olKey: string }>();
  const navigate = useNavigate();

  // React Router v6 auto-decodes URL params; decodeURIComponent is safe to call again
  const olKey = decodeURIComponent(encodedOlKey ?? '');

  const { data: book, isPending, isError } = useBookDetail(olKey);
  const addToShelf = useAddToShelf();
  const cachedShelfEntry = useShelfEntryForBook(olKey);

  // Track the entry from a successful add in this session (reactive D-16 for fresh adds)
  const [addedEntry, setAddedEntry] = useState<Pick<ShelfEntry, 'entryId' | 'status'> | null>(null);

  // Use the most recent shelf entry: just-added takes precedence over cache
  const shelfEntry = addedEntry ?? cachedShelfEntry;

  const handleAdd = (status: ShelfStatus) => {
    addToShelf.mutate(
      { olKey, status },
      {
        onSuccess: (data) => {
          setAddedEntry({ entryId: data.entryId, status: data.status });
        },
      },
    );
  };

  // Loading skeleton
  if (isPending) {
    return (
      <div className="p-4 max-w-md mx-auto animate-pulse">
        <div className="h-4 bg-muted rounded w-16 mb-4" />
        <div className="flex gap-4 mb-6">
          <div className="w-28 h-40 bg-muted rounded flex-shrink-0" />
          <div className="flex-1 space-y-3 pt-2">
            <div className="h-5 bg-muted rounded w-3/4" />
            <div className="h-4 bg-muted rounded w-1/2" />
            <div className="h-3 bg-muted rounded w-1/3" />
          </div>
        </div>
        <div className="space-y-2">
          <div className="h-3 bg-muted rounded" />
          <div className="h-3 bg-muted rounded" />
          <div className="h-3 bg-muted rounded w-4/5" />
        </div>
      </div>
    );
  }

  // Error state
  if (isError || !book) {
    return (
      <div className="p-4 text-center">
        <p className="text-sm text-destructive">Couldn&apos;t load book details. Check your connection and try again.</p>
        <Button variant="outline" size="sm" onClick={() => navigate(-1)} className="mt-3">
          Go back
        </Button>
      </div>
    );
  }

  return (
    <div className="p-4 max-w-md mx-auto pb-16">
      {/* Back navigation */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-1 text-sm text-muted-foreground mb-4 hover:text-foreground transition-colors"
        aria-label="Go back"
      >
        <ArrowLeft size={16} />
        Back
      </button>

      {/* Cover + metadata header */}
      <div className="flex gap-4 mb-6">
        <BookCoverImage
          key={book.coverId ?? book.olKey}
          coverId={book.coverId}
          title={book.title}
          className="w-28 h-40 object-cover rounded shadow-sm flex-shrink-0"
        />
        <div className="flex-1 min-w-0">
          <h1 className="text-xl font-semibold leading-tight">{book.title}</h1>
          {book.authors && (
            <p className="text-sm text-muted-foreground mt-1">{book.authors}</p>
          )}
          {book.firstPublishYear && (
            <p className="text-xs text-muted-foreground mt-1">
              First published {book.firstPublishYear}
            </p>
          )}
          {/* Page count — only shown when not null */}
          {book.pageCount != null && (
            <p className="text-xs text-muted-foreground mt-1">
              {book.pageCount} pages
            </p>
          )}
        </div>
      </div>

      {/* Description — rendered as plain text node (T-06-05: no dangerouslySetInnerHTML) */}
      {book.description && (
        <div className="mb-6">
          <h2 className="text-base font-semibold mb-2">About this book</h2>
          <p className="text-sm text-foreground leading-relaxed">{book.description}</p>
        </div>
      )}

      {/* Shelf actions — D-15 + D-16 */}
      <div className="space-y-3">
        {shelfEntry ? (
          /* D-16: Already on shelf — show badge + View on Shelf link */
          <div className="flex flex-col gap-3">
            <Badge variant="secondary" className="self-start text-sm px-3 py-1">
              {`On shelf: ${STATUS_LABELS[shelfEntry.status]}`}
            </Badge>
            <Button variant="outline" asChild>
              <Link to={`/shelf/${shelfEntry.entryId}/edit`}>View on Shelf</Link>
            </Button>
          </div>
        ) : (
          /* D-15: Not on shelf — show 3 add buttons */
          <div className="space-y-2">
            <p className="text-sm font-medium text-muted-foreground">Add to shelf</p>
            {ADD_STATUSES.map((status) => (
              <Button
                key={status}
                variant="outline"
                className="w-full"
                onClick={() => handleAdd(status)}
                disabled={addToShelf.isPending}
                aria-label={STATUS_LABELS[status]}
              >
                {addToShelf.isPending && addToShelf.variables?.status === status ? (
                  <Loader2 className="animate-spin w-4 h-4 mr-2" />
                ) : null}
                {STATUS_LABELS[status]}
              </Button>
            ))}
          </div>
        )}

        {/* Add mutation error */}
        {addToShelf.isError && (
          <p className="text-sm text-destructive">
            {(addToShelf.error as { response?: { data?: { message?: string } } })?.response?.data
              ?.message ?? 'Could not add to shelf. Please try again.'}
          </p>
        )}
      </div>
    </div>
  );
}
