package com.booktracker.books;

import java.util.List;

/**
 * API response record for book search results (D-06).
 *
 * <p>Contains the five fields exposed on the search path:
 * {@code olKey} (the Open Library work key), {@code title},
 * {@code authors}, {@code coverId}, and {@code firstPublishYear}.
 *
 * <p>{@code pageCount} is intentionally excluded from search results per D-06
 * (Open Library {@code /search.json} omits reliable page-count data;
 * it is only available on the detail path via {@code /works/{key}.json}).
 *
 * <p>All fields are nullable — Open Library may omit {@code cover_i}
 * or {@code author_name} for older or incomplete records.
 */
public record BookSearchResultDto(
        String olKey,
        String title,
        List<String> authors,
        String coverId,
        Integer firstPublishYear
) {}
