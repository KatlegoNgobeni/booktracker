package com.booktracker.books;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * HTTP client wrapper for the Open Library {@code /search.json} API.
 *
 * <p>Injects the {@code openLibraryRestClient} bean configured in
 * {@link OpenLibraryConfig} (User-Agent header + connect/read timeouts).
 *
 * <p>Pitfall mitigations (03-RESEARCH.md):
 * <ul>
 *   <li>Pitfall 1 — page 1-based: Open Library uses 1-based pagination;
 *       the controller contract is 0-based (D-04), so {@code page + 1} is passed.</li>
 *   <li>Pitfall 2 — explicit fields: the {@code fields} param is always sent to
 *       prevent the 2025 Open Library payload reduction from silently omitting data.</li>
 *   <li>Pitfall 3 — cover_i integer→String: {@code cover_i} is an {@code Integer}
 *       in the DTO; it is null-guarded before {@code String.valueOf} conversion.</li>
 *   <li>Null response / null docs → empty list, never a NullPointerException.</li>
 * </ul>
 */
@Component
public class OpenLibraryClient {

    private final RestClient restClient;

    public OpenLibraryClient(@Qualifier("openLibraryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Search Open Library for books matching the query string.
     *
     * @param q    search query (title, author, ISBN — forwarded verbatim to Open Library)
     * @param page 0-based page index (converted to 1-based for the Open Library API)
     * @param size number of results per page
     * @return list of mapped {@link BookSearchResultDto}; empty list if response is null
     */
    public List<BookSearchResultDto> search(String q, int page, int size) {
        OpenLibrarySearchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search.json")
                        .queryParam("q", q)
                        .queryParam("fields", "key,title,author_name,cover_i,first_publish_year")
                        .queryParam("page", page + 1)
                        .queryParam("limit", size)
                        .build())
                .retrieve()
                .body(OpenLibrarySearchResponse.class);

        if (response == null || response.getDocs() == null) {
            return List.of();
        }

        return response.getDocs().stream()
                .map(doc -> new BookSearchResultDto(
                        doc.getKey(),
                        doc.getTitle(),
                        doc.getAuthorName(),
                        doc.getCoverI() != null ? String.valueOf(doc.getCoverI()) : null,
                        doc.getFirstPublishYear()
                ))
                .toList();
    }
}
