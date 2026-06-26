package com.booktracker.books;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * HTTP client wrapper for the Open Library API.
 *
 * <p>Provides two operations:
 * <ol>
 *   <li>{@link #search} — proxies {@code /search.json} (Plan 01)</li>
 *   <li>{@link #getWork} — fetches {@code /works/{olKey}.json} with caching via BookService (Plan 02)</li>
 * </ol>
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
 *
 * <p>Security (T-03-05): The {@code olKey} in {@link #getWork} is bound only as a
 * {@code {olKey}} URI-template variable on the fixed base URL configured via
 * {@code openlibrary.base-url}. RestClient URL-encodes path variables and never
 * follows arbitrary user-supplied URLs — no SSRF vector.
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
        OpenLibrarySearchResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search.json")
                            .queryParam("q", q)
                            .queryParam("fields", "key,title,author_name,cover_i,first_publish_year")
                            .queryParam("page", page + 1)
                            .queryParam("limit", size)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Open Library search failed: " + res.getStatusCode());
                    })
                    .body(OpenLibrarySearchResponse.class);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Open Library is temporarily unavailable");
        }

        if (response == null || response.getDocs() == null) {
            return List.of();
        }

        return response.getDocs().stream()
                .map(doc -> new BookSearchResultDto(
                        doc.getKey().replaceFirst("^/works/", ""),
                        doc.getTitle(),
                        doc.getAuthorName(),
                        doc.getCoverI() != null ? String.valueOf(doc.getCoverI()) : null,
                        doc.getFirstPublishYear()
                ))
                .toList();
    }

    /**
     * Fetch a work's detail from Open Library.
     *
     * <p>Maps Open Library error responses:
     * <ul>
     *   <li>HTTP 404 → {@code ResponseStatusException(NOT_FOUND, "Book not found in Open Library: {olKey}")}</li>
     *   <li>Connect/read timeout ({@code ResourceAccessException}) → {@code ResponseStatusException(SERVICE_UNAVAILABLE)}</li>
     * </ul>
     *
     * <p>The {@code olKey} is passed only as a URI-template variable (e.g. {@code /works/OL45804W.json})
     * — it is never concatenated into the base URL (T-03-05 SSRF mitigation).
     *
     * @param olKey the full Open Library work key (e.g. {@code /works/OL45804W})
     * @return the deserialized work response
     * @throws ResponseStatusException with {@code NOT_FOUND} if Open Library returns 404
     * @throws ResponseStatusException with {@code SERVICE_UNAVAILABLE} on connect/read timeout
     */
    public OpenLibraryWorkResponse getWork(String olKey) {
        try {
            return restClient.get()
                    .uri("/works/{olKey}.json", olKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) {
                            throw new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Book not found in Open Library: " + olKey);
                        }
                        throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "Open Library returned an error: " + res.getStatusCode());
                    })
                    .body(OpenLibraryWorkResponse.class);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Open Library is temporarily unavailable");
        }
    }
}
