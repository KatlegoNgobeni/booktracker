package com.booktracker.books;

import com.booktracker.security.JwtUtil;
import com.booktracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvcTest slice for BookController — validates request/response shapes
 * for GET /books/search WITHOUT loading the full security context.
 *
 * <p>Security is excluded via {@code excludeAutoConfiguration} (same pattern as
 * {@code AuthControllerTest}) — these tests focus on input validation and
 * response shape, not security enforcement.
 *
 * <p>{@code JwtUtil} and {@code UserService} are mocked because
 * {@code JwtAuthenticationFilter} is a {@code @Component} that loads into
 * the {@code @WebMvcTest} context even when SecurityAutoConfiguration is excluded.
 *
 * <p>BOOK-01 acceptance criteria covered here:
 * <ul>
 *   <li>GET /books/search?q=dune returns 200 with olKey/title/coverId/firstPublishYear</li>
 *   <li>Response does NOT contain a pageCount key (D-06)</li>
 *   <li>GET /books/search with blank q returns 400</li>
 * </ul>
 */
@WebMvcTest(
    controllers = BookController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    /**
     * Mock JwtUtil — required because JwtAuthenticationFilter is a @Component that gets
     * loaded into the @WebMvcTest context even when SecurityAutoConfiguration is excluded.
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    /**
     * Mock UserService — satisfies JwtAuthenticationFilter's UserDetailsService injection.
     */
    @MockitoBean
    private UserService userService;

    /**
     * BOOK-01: valid search returns 200 and a JSON array with the expected D-06 fields.
     * The response must NOT contain a "pageCount" key.
     */
    @Test
    void search_validQuery_returns200WithExpectedShape() throws Exception {
        var dto = new BookSearchResultDto("OL123W", "Dune", List.of("Frank Herbert"), "123", 1965);
        when(bookService.search("dune", 0, 10)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/books/search").param("q", "dune"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].olKey").value("OL123W"))
               .andExpect(jsonPath("$[0].title").value("Dune"))
               .andExpect(jsonPath("$[0].coverId").value("123"))
               .andExpect(jsonPath("$[0].firstPublishYear").value(1965))
               .andExpect(jsonPath("$[0]", not(hasKey("pageCount"))));
    }

    /**
     * BOOK-01: blank q must return 400 Bad Request (Bean Validation / @NotBlank).
     */
    @Test
    void search_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/api/books/search").param("q", " "))
               .andExpect(status().isBadRequest());
    }
}
