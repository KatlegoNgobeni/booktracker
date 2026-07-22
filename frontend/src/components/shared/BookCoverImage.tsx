/**
 * BookCoverImage.tsx — Book cover with gradient fallback (D-12)
 *
 * IMPORTANT: Always key this component by coverId in the parent:
 *   <BookCoverImage key={entry.coverId ?? entry.olKey} coverId={entry.coverId} title={entry.title} />
 * This ensures React remounts on cover change rather than retrying a failed URL
 * via the same component instance (prevents infinite re-render — Pitfall 5).
 *
 * Gradient fallback: deterministic HSL from title hash, hue range 160-339.
 */
import { useState } from 'react';

interface Props {
  coverId: string | null;
  title: string;
  olKey?: string;   // fallback: OLID cover URL when coverId is null
  className?: string;
}

function titleToHue(title: string): number {
  // Hash first 3 chars to hue range 160–339 (avoids reds/oranges)
  const chars = title.slice(0, 3);
  const hash = chars.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0);
  return 160 + (hash % 180); // 160..339
}

export function BookCoverImage({ coverId, title, olKey, className }: Props) {
  const [imgFailed, setImgFailed] = useState(false);
  const hue = titleToHue(title);

  const coverSrc = coverId
    ? `https://covers.openlibrary.org/b/id/${coverId}-M.jpg`
    : olKey
    ? `https://covers.openlibrary.org/b/olid/${olKey}-M.jpg`
    : null;

  if (imgFailed || !coverSrc) {
    return (
      <div
        className={`flex items-center justify-center text-white font-semibold text-xl ${className ?? ''}`}
        style={{ background: `hsl(${hue}, 60%, 40%)` }}
        aria-label={title}
      >
        {title.charAt(0).toUpperCase()}
      </div>
    );
  }

  return (
    <img
      src={coverSrc}
      alt={title}
      className={className}
      onError={() => setImgFailed(true)}
    />
  );
}
