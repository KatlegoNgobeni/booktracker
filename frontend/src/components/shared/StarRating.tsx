/**
 * StarRating.tsx — 5-star display/input component
 *
 * Props:
 * - value: current rating (1-5) or null for unrated
 * - onChange: callback for interactive mode (omit for read-only)
 * - readOnly: renders spans instead of buttons; defaults to false
 *
 * Interactive buttons meet 44px minimum tap area per D-11.
 */
import { Star } from 'lucide-react';

interface Props {
  value: number | null;
  onChange?: (rating: number) => void;
  readOnly?: boolean;
}

export function StarRating({ value, onChange, readOnly = false }: Props) {
  return (
    <div className="flex gap-1" role="group" aria-label="Star rating">
      {[1, 2, 3, 4, 5].map((star) => {
        const filled = value !== null && star <= (value ?? 0);
        const starClass = filled
          ? 'fill-amber-400 text-amber-400'
          : 'text-muted-foreground';

        if (readOnly) {
          return (
            <Star
              key={star}
              size={16}
              className={starClass}
            />
          );
        }

        return (
          <button
            key={star}
            type="button"
            onClick={() => onChange?.(star)}
            aria-label={`Rate ${star} star${star !== 1 ? 's' : ''}`}
            className="min-h-[44px] min-w-[44px] flex items-center justify-center"
          >
            <Star size={20} className={starClass} />
          </button>
        );
      })}
    </div>
  );
}
