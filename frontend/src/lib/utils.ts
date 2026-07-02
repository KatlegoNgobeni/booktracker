import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * formatRelativeDate — human-readable relative timestamp with no external dependency.
 *
 * Returns: "today" | "yesterday" | "N days ago" | "N week(s) ago" |
 *          "N month(s) ago" | "N year(s) ago"
 *
 * Input: ISO date string (YYYY-MM-DD from LocalDate) or ISO OffsetDateTime.
 * Uses floored day difference so "today" means the same calendar day, not "< 24 hours".
 */
export function formatRelativeDate(isoDate: string): string {
  const date = new Date(isoDate);
  const now = new Date();
  const diffDays = Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24));

  if (diffDays === 0) return 'today';
  if (diffDays === 1) return 'yesterday';
  if (diffDays < 7) return `${diffDays} days ago`;
  if (diffDays < 30) return `${Math.floor(diffDays / 7)} week${diffDays < 14 ? '' : 's'} ago`;
  if (diffDays < 365) return `${Math.floor(diffDays / 30)} month${diffDays < 60 ? '' : 's'} ago`;
  return `${Math.floor(diffDays / 365)} year${diffDays < 730 ? '' : 's'} ago`;
}
