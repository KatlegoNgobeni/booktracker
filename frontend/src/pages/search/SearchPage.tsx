/**
 * SearchPage.tsx — Discovery screen: search books (D-13/D-14) and readers (DISC-01, D-05/D-06)
 *
 * UX flow (Books tab):
 * 1. User types — debounced 400ms to useBookSearch (enabled only for non-empty query)
 * 2. Form submit (Enter) — triggers search immediately (bypasses debounce)
 * 3. Results rendered as shadcn Cards; each links to /books/:olKey
 * 4. "Load more" shown when last page returned 10 results (array-based, non-Page pagination)
 *
 * UX flow (People tab):
 * 1. User types — debounced 400ms to useUserSearch (enabled only for non-empty query)
 * 2. Results rendered as non-clickable rows: Avatar + display name + FriendRequestButton
 * 3. No Link wrapper on results (D-06 — tapping the name is NOT a navigation)
 *
 * Threat mitigations:
 * - T-06-07: query debounced 400ms; enabled only for non-empty input
 * - T-09-16: display names rendered via JSX text interpolation only; no dangerouslySetInnerHTML
 */
import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { Input } from '../../components/ui/input';
import { Button } from '../../components/ui/button';
import { Card, CardContent } from '../../components/ui/card';
import { Tabs, TabsList, TabsTrigger } from '../../components/ui/tabs';
import { BookCoverImage } from '../../components/shared/BookCoverImage';
import { FriendRequestButton } from '../../components/shared/FriendRequestButton';
import { UserAvatar } from '../../components/shared/UserAvatar';
import { useBookSearch } from '../../hooks/useBooks';
import { useUserSearch } from '../../hooks/useSocial';
import type { UserSearchResult } from '../../types/api.types';

// ────────────────────────────────────────────────────────
// People result row
// ────────────────────────────────────────────────────────

function PeopleResultRow({ user }: { user: UserSearchResult }) {
  return (
    <div className="flex items-center gap-3 p-3 rounded-lg border bg-card">
      {/* AVATAR-05: generated avatar seeded by user UUID (initials fallback built in) */}
      <UserAvatar userId={user.id} displayName={user.displayName} size="default" photoUrl={user.photoUrl ?? null} />
      <div className="flex-1 min-w-0">
        {/* T-09-16: plain JSX text — no dangerouslySetInnerHTML */}
        <p className="text-sm font-semibold text-foreground truncate">{user.displayName}</p>
      </div>
      <FriendRequestButton
        userId={user.id}
        requestId={user.requestId}
        status={user.friendStatus}
      />
    </div>
  );
}

// ────────────────────────────────────────────────────────
// SearchPage
// ────────────────────────────────────────────────────────

export function SearchPage() {
  const [activeTab, setActiveTab] = useState<'books' | 'people'>('books');
  const [inputValue, setInputValue] = useState('');
  const [query, setQuery] = useState('');

  // Debounce: update query 400ms after inputValue changes (D-13)
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(inputValue);
    }, 400);
    return () => clearTimeout(timer);
  }, [inputValue]);

  // Minimum 2 chars before firing — 1-char queries reliably fail on Open Library
  const searchEnabled = query.trim().length >= 2;

  const {
    data: bookData,
    isPending: booksPending,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
    isError: booksError,
    refetch: refetchBooks,
  } = useBookSearch(activeTab === 'books' && searchEnabled ? query : '');

  const {
    data: peopleData,
    isPending: peoplePending,
    isError: peopleError,
    refetch: refetchPeople,
  } = useUserSearch(activeTab === 'people' && searchEnabled ? query : '');

  const allBooks = bookData?.pages.flat() ?? [];
  const people = peopleData?.content ?? [];

  // Form submit: trigger search immediately (bypasses debounce — useful on slow typing)
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setQuery(inputValue);
  };

  return (
    <div className="pb-16">
      <h1 className="text-[28px] font-semibold mb-4">Search</h1>

      {/* Tab bar — Books / People */}
      <Tabs
        value={activeTab}
        onValueChange={(v) => setActiveTab(v as 'books' | 'people')}
        className="mb-4"
      >
        <TabsList className="w-full">
          <TabsTrigger value="books" className="flex-1">Books</TabsTrigger>
          <TabsTrigger value="people" className="flex-1">People</TabsTrigger>
        </TabsList>
      </Tabs>

      {/* Search form — placeholder changes by tab */}
      <form onSubmit={handleSubmit} role="search" aria-label="Book search">
        <Input
          type="search"
          placeholder={
            activeTab === 'books'
              ? 'Search by title or author…'
              : 'Search readers by name…'
          }
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          aria-label="Search books"
          className="mb-4"
        />
      </form>

      {/* ── BOOKS TAB ───────────────────────────── */}
      {activeTab === 'books' && (
        <>
          {/* Empty state — no query entered yet */}
          {query.trim().length === 0 && (
            <div className="text-center py-12">
              <h2 className="text-xl font-semibold">Find your next book</h2>
              <p className="text-sm text-muted-foreground mt-2">
                Search by title or author to discover books.
              </p>
            </div>
          )}

          {/* Keep typing hint — 1-char queries fail on Open Library */}
          {query.trim().length === 1 && (
            <div className="text-center py-12">
              <p className="text-sm text-muted-foreground">Keep typing to search…</p>
            </div>
          )}

          {/* Loading skeleton — 3 placeholder cards (UI-SPEC) */}
          {searchEnabled && booksPending && (
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
          {booksError && (
            <div className="text-center py-8">
              <p className="text-sm text-destructive">Search failed. Try again in a moment.</p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetchBooks()}
                className="mt-2"
              >
                Retry
              </Button>
            </div>
          )}

          {/* Results list */}
          {!booksPending && allBooks.length > 0 && (
            <div className="space-y-3" data-testid="book-search-results">
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
                        className="aspect-[2/3] w-16 object-cover rounded-md flex-shrink-0"
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

          {/* Empty results state — query submitted but returned nothing */}
          {searchEnabled && !booksPending && !booksError && allBooks.length === 0 && (
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
        </>
      )}

      {/* ── PEOPLE TAB ──────────────────────────── */}
      {activeTab === 'people' && (
        <>
          {/* Empty state — no query */}
          {query.trim().length === 0 && (
            <div className="text-center py-12">
              <h2 className="text-xl font-semibold">Find readers</h2>
              <p className="text-sm text-muted-foreground mt-2">
                Search by display name to find other readers.
              </p>
            </div>
          )}

          {/* Keep typing hint */}
          {query.trim().length === 1 && (
            <div className="text-center py-12">
              <p className="text-sm text-muted-foreground">Keep typing to search…</p>
            </div>
          )}

          {/* Loading skeleton */}
          {searchEnabled && peoplePending && (
            <div className="space-y-3">
              {[0, 1, 2].map((i) => (
                <div key={i} className="flex items-center gap-3 p-3 rounded-lg border animate-pulse">
                  <div className="h-8 w-8 rounded-full bg-muted shrink-0" />
                  <div className="h-4 bg-muted rounded w-1/2" />
                  <div className="h-7 w-20 bg-muted rounded ml-auto" />
                </div>
              ))}
            </div>
          )}

          {/* Error state */}
          {peopleError && (
            <div className="text-center py-8">
              <p className="text-sm text-destructive">Search failed. Try again in a moment.</p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => refetchPeople()}
                className="mt-2"
              >
                Retry
              </Button>
            </div>
          )}

          {/* People results — non-clickable rows (D-06: no Link wrapper) */}
          {searchEnabled && !peoplePending && !peopleError && people.length > 0 && (
            <div className="space-y-3">
              {people.map((user) => (
                <PeopleResultRow key={user.id} user={user} />
              ))}
            </div>
          )}

          {/* Empty results — query submitted but no users found */}
          {searchEnabled && !peoplePending && !peopleError && people.length === 0 && (
            <p className="text-sm text-muted-foreground text-center py-12">
              No readers found for that name. Try different keywords.
            </p>
          )}
        </>
      )}
    </div>
  );
}
