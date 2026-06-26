/**
 * SearchPage.tsx — Discovery screen: search Open Library proxy, paginate results (D-13/D-14)
 *
 * UX flow:
 * 1. User types — debounced 400ms to useBookSearch (enabled only for non-empty query)
 * 2. Form submit (Enter) — triggers search immediately (bypasses debounce)
 * 3. Results rendered as shadcn Cards; each links to /books/:olKey
 * 4. "Load more" shown when last page returned 10 results (array-based, non-Page pagination)
 *
 * Threat mitigations:
 * - T-06-07: query debounced 400ms; enabled only for non-empty input
 */
import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { Input } from '../../components/ui/input';
import { Button } from '../../components/ui/button';
import { Card, CardContent } from '../../components/ui/card';
import { BookCoverImage } from '../../components/shared/BookCoverImage';
import { useBookSearch } from '../../hooks/useBooks';

export function SearchPage() {
  const [inputValue, setInputValue] = useState('');
  const [query, setQuery] = useState('');

  // Debounce: update query 400ms after inputValue changes (D-13)
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(inputValue);
    }, 400);
    return () => clearTimeout(timer);
  }, [inputValue]);

  const {
    data,
    isPending,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
    isError,
    refetch,
  } = useBookSearch(query);

  const allBooks = data?.pages.flat() ?? [];

  // Form submit: trigger search immediately (bypasses debounce — useful on slow typing)
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setQuery(inputValue);
  };

  return (
    <div className="p-4 pb-16">
      {/* Search form — role="search" for accessibility and test targeting */}
      <form onSubmit={handleSubmit} role="search" aria-label="Book search">
        <Input
          type="search"
          placeholder="Search by title or author..."
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          aria-label="Search books"
          className="mb-4"
        />
      </form>

      {/* Empty state — no query entered yet */}
      {!query && (
        <div className="text-center py-12">
          <h2 className="text-xl font-semibold">Find your next book</h2>
          <p className="text-sm text-muted-foreground mt-2">
            Search by title or author to discover books.
          </p>
        </div>
      )}

      {/* Loading skeleton — 3 placeholder cards (UI-SPEC) */}
      {query && isPending && (
        <div className="space-y-3" aria-label="Loading results">
          {[0, 1, 2].map((i) => (
            <div key={i} className="animate-pulse flex gap-3 p-3 rounded-lg border">
              <div className="w-16 h-24 bg-muted rounded flex-shrink-0" />
              <div className="flex-1 space-y-2 py-1">
                <div className="h-4 bg-muted rounded w-3/4" />
                <div className="h-3 bg-muted rounded w-1/2" />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Error state */}
      {isError && (
        <div className="text-center py-8">
          <p className="text-sm text-destructive">Search failed. Try again in a moment.</p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            className="mt-2"
          >
            Retry
          </Button>
        </div>
      )}

      {/* Results list */}
      {!isPending && allBooks.length > 0 && (
        <div className="space-y-3">
          {allBooks.map((book) => (
            <Link
              key={book.olKey}
              to={`/books/${encodeURIComponent(book.olKey)}`}
            >
              <Card className="hover:shadow-md transition-shadow">
                <CardContent className="flex gap-3 p-3">
                  <BookCoverImage
                    key={book.coverId ?? book.olKey}
                    coverId={book.coverId}
                    title={book.title}
                    className="w-16 h-24 object-cover rounded flex-shrink-0"
                  />
                  <div className="min-w-0">
                    <p className="text-base font-semibold line-clamp-2">
                      {book.title}
                    </p>
                    {book.authors && book.authors.length > 0 && (
                      <p className="text-sm text-muted-foreground mt-1">
                        {book.authors.join(', ')}
                      </p>
                    )}
                    {book.firstPublishYear && (
                      <p className="text-xs text-muted-foreground mt-1">
                        {book.firstPublishYear}
                      </p>
                    )}
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}

      {/* Empty results state — query was submitted but returned nothing */}
      {query && !isPending && !isError && allBooks.length === 0 && (
        <div className="text-center py-12">
          <h2 className="text-xl font-semibold">No books found for that search</h2>
          <p className="text-sm text-muted-foreground mt-2">
            Try different keywords or check the spelling.
          </p>
        </div>
      )}

      {/* Load more — shown only when last page returned exactly 10 results (D-14) */}
      {hasNextPage && (
        <div className="text-center mt-4">
          <Button
            variant="outline"
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
          >
            {isFetchingNextPage && (
              <Loader2 className="animate-spin w-4 h-4 mr-2" />
            )}
            Load more
          </Button>
        </div>
      )}
    </div>
  );
}
