/**
 * DiscoveryBookCard.tsx — Tappable book card for horizontal discovery scroll rows (DISC-02, DISC-05)
 *
 * Each card is a React Router Link to /books/:olKey, wrapping a book cover image
 * and title. Used inside DiscoverySection rows.
 *
 * Layout notes:
 * - `flex-shrink-0 w-24` prevents cards from collapsing in the flex scroll container.
 * - `snap-start` snaps each card to position in the parent `snap-x` container (DISC-05).
 * - `key={coverId ?? olKey}` on BookCoverImage ensures React remounts on cover change
 *   rather than retrying a failed URL (RESEARCH Pitfall 3).
 *
 * Security: T-15-05 — title is rendered via JSX text interpolation; no dangerouslySetInnerHTML.
 */
import { Link } from 'react-router-dom';
import { BookCoverImage } from '../shared/BookCoverImage';

interface DiscoveryBookCardProps {
  title: string;
  coverId: string | null;
  olKey: string;
}

export function DiscoveryBookCard({ title, coverId, olKey }: DiscoveryBookCardProps) {
  return (
    <Link
      to={`/books/${olKey}`}
      className="flex-shrink-0 w-24 snap-start"
      aria-label={title}
    >
      <BookCoverImage
        key={coverId ?? olKey}
        coverId={coverId}
        olKey={olKey}
        title={title}
        className="aspect-[2/3] w-24 object-cover rounded-md"
      />
      <p className="text-xs text-muted-foreground mt-1 line-clamp-2">{title}</p>
    </Link>
  );
}
