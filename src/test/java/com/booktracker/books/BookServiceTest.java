package com.booktracker.books;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BookService} — search path (Plan 01) and
 * cache-or-fetch detail path (Plan 02).
 *
 * <p>Uses {@code @ExtendWith(MockitoExtension.class)} with mocked
 * {@code BookRepository} and {@code OpenLibraryClient} — no Spring context needed
 * (same pattern as {@code UserServiceTest}).
 *
 * <p>BOOK-01 acceptance criteria (search path):
 * <ul>
 *   <li>search delegates q/page/size to OpenLibraryClient and returns its result unchanged</li>
 *   <li>a search result row with null coverId and null authors is returned without throwing</li>
 * </ul>
 *
 * <p>BOOK-02 + BOOK-03 acceptance criteria (detail path):
 * <ul>
 *   <li>Cache hit: findByOpenLibraryKey returns present → maps entity; getWork NOT called</li>
 *   <li>Cache miss: findByOpenLibraryKey empty → getWork called → save called → DTO returned</li>
 *   <li>Open Library 404 propagates as ResponseStatusException(NOT_FOUND)</li>
 *   <li>Open Library timeout propagates as ResponseStatusException(SERVICE_UNAVAILABLE)</li>
 *   <li>Null pageCount / null covers → no throw; coverId is null</li>
 *   <li>Concurrent save race: DataIntegrityViolationException → recovered by re-running findByOpenLibraryKey</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private OpenLibraryClient openLibraryClient;

    @InjectMocks
    private BookService bookService;

    // ----------------------------------------------------------------
    // BOOK-01: search path tests (unchanged from Plan 01)
    // ----------------------------------------------------------------

    /**
     * BOOK-01: search delegates to OpenLibraryClient and returns its list unchanged.
     */
    @Test
    void search_delegatesToClientAndReturnsList() {
        var dto1 = new BookSearchResultDto("/works/OL123W", "Dune", List.of("Frank Herbert"), "123", 1965);
        var dto2 = new BookSearchResultDto("/works/OL456W", "Dune Messiah", List.of("Frank Herbert"), "456", 1969);
        when(openLibraryClient.search("dune", 0, 10)).thenReturn(List.of(dto1, dto2));

        List<BookSearchResultDto> result = bookService.search("dune", 0, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).olKey()).isEqualTo("/works/OL123W");
        assertThat(result.get(1).olKey()).isEqualTo("/works/OL456W");
    }

    /**
     * BOOK-01: a result row with null coverId and null authors must not throw.
     */
    @Test
    void search_rowWithNullCoverAndNullAuthors_doesNotThrow() {
        var dto = new BookSearchResultDto("/works/OL789W", "Unknown Book", null, null, null);
        when(openLibraryClient.search("unknown", 0, 10)).thenReturn(List.of(dto));

        assertThatNoException().isThrownBy(() -> {
            List<BookSearchResultDto> result = bookService.search("unknown", 0, 10);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).coverId()).isNull();
            assertThat(result.get(0).authors()).isNull();
        });
    }

    // ----------------------------------------------------------------
    // BOOK-02: cache-or-fetch detail path tests
    // ----------------------------------------------------------------

    /**
     * BOOK-02: Cache hit — findByOpenLibraryKey returns present → maps entity to DTO;
     * OpenLibraryClient.getWork must NOT be called.
     */
    @Test
    void getOrFetch_cacheHit_returnsEntityDtoWithoutCallingGetWork() {
        BookEntity entity = new BookEntity();
        entity.setOpenLibraryKey("/works/OL45804W");
        entity.setTitle("Fantastic Mr Fox");
        entity.setAuthors(null);
        entity.setCoverId("24195");
        entity.setPageCount(96);
        entity.setFirstPublishYear(null);

        when(bookRepository.findByOpenLibraryKey("/works/OL45804W"))
                .thenReturn(Optional.of(entity));

        BookDetailDto dto = bookService.getOrFetch("/works/OL45804W");

        assertThat(dto.olKey()).isEqualTo("/works/OL45804W");
        assertThat(dto.title()).isEqualTo("Fantastic Mr Fox");
        assertThat(dto.coverId()).isEqualTo("24195");
        assertThat(dto.pageCount()).isEqualTo(96);

        // Verify getWork was NOT called on cache hit (D-02)
        verify(openLibraryClient, never()).getWork(any());
    }

    /**
     * BOOK-02: Cache miss — findByOpenLibraryKey empty → getWork called → save called → DTO returned.
     */
    @Test
    void getOrFetch_cacheMiss_fetchesFromOpenLibraryAndSaves() {
        OpenLibraryWorkResponse work = new OpenLibraryWorkResponse();
        work.setKey("/works/OL45804W");
        work.setTitle("Fantastic Mr Fox");
        work.setPageCount(96);
        work.setCovers(List.of(24195));

        BookEntity savedEntity = new BookEntity();
        savedEntity.setOpenLibraryKey("/works/OL45804W");
        savedEntity.setTitle("Fantastic Mr Fox");
        savedEntity.setCoverId("24195");
        savedEntity.setPageCount(96);

        when(bookRepository.findByOpenLibraryKey("/works/OL45804W"))
                .thenReturn(Optional.empty());
        when(openLibraryClient.getWork("/works/OL45804W")).thenReturn(work);
        when(bookRepository.save(any(BookEntity.class))).thenReturn(savedEntity);

        BookDetailDto dto = bookService.getOrFetch("/works/OL45804W");

        assertThat(dto.olKey()).isEqualTo("/works/OL45804W");
        assertThat(dto.title()).isEqualTo("Fantastic Mr Fox");
        assertThat(dto.coverId()).isEqualTo("24195");
        assertThat(dto.pageCount()).isEqualTo(96);

        verify(openLibraryClient).getWork("/works/OL45804W");
        verify(bookRepository).save(any(BookEntity.class));
    }

    /**
     * BOOK-03: Open Library 404 → getWork throws ResponseStatusException(NOT_FOUND) → propagates.
     */
    @Test
    void getOrFetch_openLibrary404_propagatesNotFound() {
        when(bookRepository.findByOpenLibraryKey("/works/OL99999W"))
                .thenReturn(Optional.empty());
        when(openLibraryClient.getWork("/works/OL99999W"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));

        assertThatThrownBy(() -> bookService.getOrFetch("/works/OL99999W"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * BOOK-03: Open Library timeout → getWork throws ResponseStatusException(SERVICE_UNAVAILABLE) → propagates.
     */
    @Test
    void getOrFetch_openLibraryTimeout_propagatesServiceUnavailable() {
        when(bookRepository.findByOpenLibraryKey("/works/OL45804W"))
                .thenReturn(Optional.empty());
        when(openLibraryClient.getWork("/works/OL45804W"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Open Library is temporarily unavailable"));

        assertThatThrownBy(() -> bookService.getOrFetch("/works/OL45804W"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    /**
     * BOOK-03: Null pageCount and null covers in work response → no throw; coverId is null.
     */
    @Test
    void getOrFetch_nullPageCountAndNullCovers_doesNotThrow() {
        OpenLibraryWorkResponse work = new OpenLibraryWorkResponse();
        work.setKey("/works/OL45804W");
        work.setTitle("Fantastic Mr Fox");
        work.setPageCount(null);
        work.setCovers(null); // covers absent

        BookEntity savedEntity = new BookEntity();
        savedEntity.setOpenLibraryKey("/works/OL45804W");
        savedEntity.setTitle("Fantastic Mr Fox");
        savedEntity.setCoverId(null); // no cover
        savedEntity.setPageCount(null);

        when(bookRepository.findByOpenLibraryKey("/works/OL45804W"))
                .thenReturn(Optional.empty());
        when(openLibraryClient.getWork("/works/OL45804W")).thenReturn(work);
        when(bookRepository.save(any(BookEntity.class))).thenReturn(savedEntity);

        assertThatNoException().isThrownBy(() -> {
            BookDetailDto dto = bookService.getOrFetch("/works/OL45804W");
            assertThat(dto.coverId()).isNull();
            assertThat(dto.pageCount()).isNull();
        });
    }

    /**
     * BOOK-02 + Pitfall 7: Concurrent save race — DataIntegrityViolationException is caught
     * and recovered by re-running findByOpenLibraryKey (TOCTOU-safe pattern).
     */
    @Test
    void getOrFetch_concurrentSaveRace_recoversViaSecondFindByKey() {
        OpenLibraryWorkResponse work = new OpenLibraryWorkResponse();
        work.setKey("/works/OL45804W");
        work.setTitle("Fantastic Mr Fox");
        work.setPageCount(96);
        work.setCovers(List.of(24195));

        BookEntity concurrentlySavedEntity = new BookEntity();
        concurrentlySavedEntity.setOpenLibraryKey("/works/OL45804W");
        concurrentlySavedEntity.setTitle("Fantastic Mr Fox");
        concurrentlySavedEntity.setCoverId("24195");
        concurrentlySavedEntity.setPageCount(96);

        when(bookRepository.findByOpenLibraryKey("/works/OL45804W"))
                .thenReturn(Optional.empty())                             // first check: cache miss
                .thenReturn(Optional.of(concurrentlySavedEntity));        // recovery check: finds row
        when(openLibraryClient.getWork("/works/OL45804W")).thenReturn(work);
        when(bookRepository.save(any(BookEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        BookDetailDto dto = bookService.getOrFetch("/works/OL45804W");

        assertThat(dto.olKey()).isEqualTo("/works/OL45804W");
        assertThat(dto.title()).isEqualTo("Fantastic Mr Fox");
    }
}
