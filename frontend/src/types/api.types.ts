/**
 * api.types.ts — TypeScript interfaces matching backend DTO contracts
 *
 * Derived from verified backend DTOs:
 * - AuthResponse.java + AuthResponse.UserDto
 * - BookSearchResultDto.java, BookDetailDto.java
 * - ShelfStatus.java (enum), ShelfEntryDto.java
 * - StatsDto.java, GoalDto.java
 *
 * IMPORTANT: BookSearchResult arrives in a plain List<BookSearchResultDto> (NOT a Page).
 * The search endpoint GET /api/books/search returns a plain JSON array, not a paginated
 * Page<T> response. Do NOT expect { content, totalPages, ... } shape for search results.
 *
 * DTO TYPE MAP:
 * Java String (UUID)       -> string
 * Java OffsetDateTime      -> string (ISO 8601 with offset: "2024-01-15T10:30:00+02:00")
 * Java LocalDate           -> string (ISO date: "2024-01-15")
 * Java Integer (nullable)  -> number | null
 * Java Double (nullable)   -> number | null
 * @JsonInclude(NON_NULL)   -> TypeScript optional (?) — field absent from JSON, not null
 */

// Derived from AuthResponse.java + AuthResponse.UserDto
export interface AuthResponse {
  token: string;
  user: {
    id: string;
    email: string;
    displayName: string;
    createdAt: string; // ISO OffsetDateTime
  };
}

// Derived from BookSearchResultDto.java
// NOTE: authors is string[] (List<String> from OL search) — unlike BookDetail which is comma-joined
export interface BookSearchResult {
  olKey: string;
  title: string;
  authors: string[] | null;
  coverId: string | null;
  firstPublishYear: number | null;
}

// Derived from BookDetailDto.java
// NOTE: authors is a comma-joined string — different from BookSearchResult.authors (string[])
export interface BookDetail {
  olKey: string;
  title: string;
  authors: string | null; // comma-joined string (not array — unlike search result)
  coverId: string | null;
  pageCount: number | null;
  firstPublishYear: number | null;
  description: string | null; // Open Library work description — may be absent (null)
}

// Derived from ShelfStatus.java (enum STRING values — @Enumerated(EnumType.STRING))
export type ShelfStatus = 'WANT_TO_READ' | 'CURRENTLY_READING' | 'READ';

// Derived from ShelfEntryDto.java
export interface ShelfEntry {
  entryId: string;
  status: ShelfStatus;
  rating: number | null;
  review: string | null;
  currentPage: number | null;
  pageCount?: number | null;    // Optional book page count — show progress bar only when present and > 0 (D-10)
  dateStarted: string | null;   // ISO LocalDate (YYYY-MM-DD)
  dateFinished: string | null;  // ISO LocalDate (YYYY-MM-DD)
  createdAt: string;            // ISO OffsetDateTime
  // Inline book summary (denormalized in ShelfEntryDto)
  title: string;
  olKey: string;
  coverId: string | null;
  authors: string | null;
}

// Derived from StatsDto.java
// Fields marked @JsonInclude(NON_NULL) are absent from JSON when null — use optional chaining
export interface StatsDto {
  booksReadAllTime: number;
  booksReadThisYear: number;
  currentlyReadingCount: number;
  goalTarget?: number;             // absent if no goal set
  goalProgressPercent?: number;    // absent if no goal set
  averageRating?: number;
  pagesReadThisYear?: number;
  averageBookLength?: number;
  booksPerMonth: number[];         // always 12-element array [Jan..Dec]
  longestBook?: { title: string; pageCount: number };
  shortestBook?: { title: string; pageCount: number };
}

// Derived from GoalDto.java
// NOTE: GoalDto has NO id field — PATTERNS.md was incorrect to include it
export interface GoalDto {
  targetCount: number;
  year: number;
}

// Spring Pageable response shape (list endpoints except book search)
export interface Page<T> {
  content: T[];
  number: number;       // 0-based current page
  size: number;
  totalPages: number;
  totalElements: number;
}

// ────────────────────────────────────────────────────────
// Social layer types (Phase 8 — SOCIAL-01/02/03)
// Derived from: PublicShelfEntryDto, PublicProfileDto, FeedItemDto, FollowStatusDto
// ────────────────────────────────────────────────────────

// Derived from PublicShelfEntryDto.java
export interface PublicShelfEntry {
  entryId: string;
  title: string;
  authors: string | null;    // comma-joined; nullable
  coverId: string | null;
  olKey: string;             // short form e.g. "OL45804W"
  rating: number | null;     // 1–5 or null
  review: string | null;
  dateFinished: string | null; // ISO date YYYY-MM-DD
}

// Derived from PublicProfileDto.java (@JsonInclude(NON_NULL) — optional fields absent when null)
export interface PublicProfile {
  userId: string;
  displayName: string;
  followerCount: number;
  followingCount: number;
  isFollowing: boolean;          // does the authenticated user follow this profile?
  goalTarget?: number;           // absent if no goal set
  goalProgressPercent?: number;  // absent if no goal set; capped at 100.0
  booksReadThisYear: number;
  readEntries: Page<PublicShelfEntry>; // paginated READ shelf
}

// Derived from FeedItemDto.java (@JsonInclude(NON_NULL))
export interface FeedItem {
  entryId: string;           // user_books UUID (dedup key + click-through)
  userId: string;            // the reader (link to /users/:id)
  displayName: string;       // the reader's display name
  bookTitle: string;
  bookOlKey: string;         // short form; link to /books/:olKey
  bookCoverId: string | null;
  bookAuthors: string | null; // comma-joined
  rating: number | null;
  review: string | null;
  dateFinished: string;      // ISO date YYYY-MM-DD (relative timestamp)
  createdAt: string;         // ISO OffsetDateTime (entry creation time)
}

// Derived from FollowStatusDto.java
export interface FollowStatus {
  following: boolean;
}
