/**
 * DiscoverySection.tsx — Generic horizontal scroll section wrapper for book discovery rows
 *
 * Used by FeedPage to render "Friends are reading", "Trending this week",
 * and "Up next for you" sections (DISC-01, DISC-05).
 *
 * Layout notes:
 * - `-mx-4 px-4` bleed trick: extends the scroll container flush to the screen edges
 *   without causing page-level horizontal overflow (RESEARCH Pitfall 2, DISC-05).
 * - `scrollbar-none` hides the scrollbar on desktop browsers.
 * - `snap-x snap-mandatory` enables iOS Safari momentum scrolling with snap points.
 *
 * Security: T-15-05 — title/subtitle are rendered via JSX text interpolation only;
 * no dangerouslySetInnerHTML.
 */

interface DiscoverySectionProps {
  title: string;
  children: React.ReactNode;
  subtitle?: string;
}

export function DiscoverySection({ title, children, subtitle }: DiscoverySectionProps) {
  return (
    <section className="mb-6">
      <h2 className="text-base font-semibold mb-1">{title}</h2>
      {subtitle && <p className="text-xs text-muted-foreground mb-2">{subtitle}</p>}
      <div className="flex gap-3 overflow-x-auto pb-2 -mx-4 px-4 scrollbar-none snap-x snap-mandatory">
        {children}
      </div>
    </section>
  );
}
