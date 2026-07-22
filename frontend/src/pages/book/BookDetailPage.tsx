/**
 * BookDetailPage.tsx — Book detail view with add-to-shelf and D-16 already-on-shelf state
 *
 * Features:
 * - D-15: Three add-to-shelf buttons (Want to Read / Currently Reading / Read)
 * - D-16: When book is already on shelf, buttons are replaced by status badge + "View on Shelf" link
 * - T-06-05: description rendered as React text node — dangerouslySetInnerHTML is NEVER used
 * - Page count only shown when not null
 *
 * Route: /books/:olKey (React Router v6 decodes the param — no manual decode)
 */
import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Loader2, BookmarkPlus } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Badge } from '../../components/ui/badge';
import { BookCoverImage } from '../../components/shared/BookCoverImage';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '../../components/ui/sheet';
import { useBookDetail, useAddToShelf, useShelfEntryForBook } from '../../hooks/useBooks';
import {
  useMyCollections,
  useCollectionDetail,
  useAddBookToCollection,
  useRemoveBookFromCollection,
} from '../../hooks/useCollections';
import type { ShelfStatus, ShelfEntry } from '../../types/api.types';

// ── Add-to-Collection sheet ──────────────────────────────

/**
 * CollectionRow — one row in the Add to Collection sheet.
 * Fetches detail on mount to check if the book's olKey is already in the collection.
 */
function CollectionRow({
  collectionId,
  collectionName,
  olKey,
}: {
  collectionId: string;
  collectionName: string;
  olKey: string;
}) {
  const { data: detail, isPending } = useCollectionDetail(collectionId);
  const addBook = useAddBookToCollection();
  const removeBook = useRemoveBookFromCollection();

  const isInCollection = detail?.olKeys.includes(olKey) ?? false;
  const isBusy = addBook.isPending || removeBook.isPending || isPending;

  const toggle = () => {
    if (isInCollection) {
      removeBook.mutate({ collectionId, olKey });
    } else {
      addBook.mutate({ collectionId, olKey });
    }
  };

  return (
    <div className="flex items-center justify-between gap-3 py-2 border-b last:border-0">
      <span className="text-sm font-medium truncate">{collectionName}</span>
      <Button
        variant={isInCollection ? 'secondary' : 'outline'}
        size="sm"
        onClick={toggle}
        disabled={isBusy}
        className="shrink-0"
      >
        {isPending ? (
          <Loader2 className="animate-spin h-3 w-3" />
        ) : isInCollection ? (
          'Remove'
        ) : (
          'Add'
        )}
      </Button>
    </div>
  );
}

function AddToCollectionSheet({ olKey }: { olKey: string }) {
  const [open, setOpen] = useState(false);
  const { data: collections, isPending } = useMyCollections();

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" className="w-full gap-2">
          <BookmarkPlus className="h-4 w-4" />
          Add to Collection
        </Button>
      </SheetTrigger>
      <SheetContent side="bottom" className="max-h-[60vh] overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Add to Collection</SheetTitle>
        </SheetHeader>
        <div className="px-4 pb-4">
          {isPending ? (
            <div className="space-y-3 pt-2">
              {[1, 2].map((i) => (
                <div key={i} className="h-10 rounded bg-muted animate-pulse" />
              ))}
            </div>
          ) : collections && collections.length > 0 ? (
            <div className="pt-2">
              {collections.map((col) => (
                <CollectionRow
                  key={col.id}
                  collectionId={col.id}
                  collectionName={col.name}
                  olKey={olKey}
                />
              ))}
            </div>
          ) : (
            <div className="py-6 text-center">
              <p className="text-sm text-muted-foreground mb-3">No collections yet.</p>
              <Button variant="outline" size="sm" asChild onClick={() => setOpen(false)}>
                <Link to="/collections">Create a collection</Link>
              </Button>
            </div>
          )}
          {collections && collections.length > 0 && (
            <div className="pt-3">
              <Button variant="ghost" size="sm" asChild className="w-full text-muted-foreground" onClick={() => setOpen(false)}>
                <Link to="/collections">+ Manage collections</Link>
              </Button>
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

const STATUS_LABELS: Record<ShelfStatus, string> = {
  WANT_TO_READ: 'Want to Read',
  CURRENTLY_READING: 'Currently Reading',
  READ: 'Read',
  ABANDONED: 'Did Not Finish',
};

const ADD_STATUSES: ShelfStatus[] = ['WANT_TO_READ', 'CURRENTLY_READING', 'READ', 'ABANDONED'];

export function BookDetailPage() {
  const { olKey: encodedOlKey } = useParams<{ olKey: string }>();
  const navigate = useNavigate();

  // React Router v6 already decodes route params — do NOT decode again.
  // A second decodeURIComponent throws URIError on any stray '%' (CR-01) and
  // is semantically wrong for olKeys legitimately containing '%'.
  const olKey = encodedOlKey ?? '';

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
      <div className="animate-pulse">
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
    <div className="pb-16">
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
          className="aspect-[2/3] w-28 object-cover rounded-md shadow-sm flex-shrink-0"
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
            <p className="text-sm font-normal text-muted-foreground">Add to shelf</p>
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

        {/* Add to Collection — always shown below shelf actions */}
        <div className="pt-2">
          <AddToCollectionSheet olKey={olKey} />
        </div>
      </div>
    </div>
  );
}
