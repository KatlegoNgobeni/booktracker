package com.booktracker.social;

import java.time.LocalDate;

/**
 * DTO for a single READ shelf entry exposed in a public profile.
 *
 * <p>Only READ entries appear in a public profile (T-08-03 information disclosure mitigation).
 * WANT_TO_READ and CURRENTLY_READING entries are never returned.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code entryId}      — user_books UUID as string</li>
 *   <li>{@code title}        — book title (always non-null)</li>
 *   <li>{@code authors}      — comma-joined authors string (nullable)</li>
 *   <li>{@code coverId}      — Open Library cover ID (nullable)</li>
 *   <li>{@code olKey}        — short-form work key, e.g. "OL45804W" (stripped of "/works/" prefix)</li>
 *   <li>{@code rating}       — user rating 1–5 (nullable)</li>
 *   <li>{@code review}       — user's review text (nullable)</li>
 *   <li>{@code dateFinished} — date user finished reading (nullable)</li>
 *   <li>{@code likeCount}    — total number of likes on this entry (DISC-04)</li>
 *   <li>{@code likedByMe}    — whether the viewing currentUser has liked this entry (DISC-04)</li>
 * </ul>
 */
public record PublicShelfEntryDto(
        String entryId,
        String title,
        String authors,
        String coverId,
        String olKey,
        Integer rating,
        String review,
        LocalDate dateFinished,
        int likeCount,
        boolean likedByMe
) {
}
