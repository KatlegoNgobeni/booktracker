package com.booktracker.books;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BookService — search path only (Plan 01).
 *
 * <p>Uses {@code @ExtendWith(MockitoExtension.class)} with a mocked
 * {@code OpenLibraryClient} — no Spring context needed (same pattern as
 * {@code UserServiceTest}).
 *
 * <p>BOOK-01 acceptance criteria covered here:
 * <ul>
 *   <li>search delegates q/page/size to OpenLibraryClient and returns its result unchanged</li>
 *   <li>a search result row with null coverId and null authors is returned without throwing</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private OpenLibraryClient openLibraryClient;

    @InjectMocks
    private BookService bookService;

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
}
